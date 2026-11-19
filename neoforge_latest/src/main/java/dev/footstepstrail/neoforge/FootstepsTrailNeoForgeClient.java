package dev.footstepstrail.neoforge;

import dev.footstepstrail.FootstepsTrail;
import dev.footstepstrail.config.FootstepsConfig;
import dev.footstepstrail.deformation.RendererCompat;
import net.neoforged.fml.ModList;
import dev.footstepstrail.neoforge.command.FootstepsCommand;
import dev.footstepstrail.render.FootstepsRenderer;
import dev.footstepstrail.tracking.FootstepsTracker;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import dev.footstepstrail.render.SurfaceSampler;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;

/**
 * Client-only NeoForge initialization.
 */
public final class FootstepsTrailNeoForgeClient {

    /** Clears the small gap between a player's collision feet and visible snow. */
    private static final double SURFACE_RAY_START_OFFSET = 0.25;

    private static KeyMapping toggleKey;
    private static final Identifier FOOTSTEPS_PASS_ID =
        Identifier.fromNamespaceAndPath(FootstepsTrail.MOD_ID, "footsteps_trail");
    private static final KeyMapping.Category KEY_CATEGORY =
        new KeyMapping.Category(Identifier.fromNamespaceAndPath(FootstepsTrail.MOD_ID, "footstepstrail"));

    private FootstepsTrailNeoForgeClient() {
    }

    public static void init(IEventBus modBus) {
        FootstepsTrail.LOGGER.info("Initializing Footsteps Trail (NeoForge client)");

        // Load configuration early
        FootstepsConfig.getInstance();
        detectRenderer();

        // Register Mod Event Bus listeners
        modBus.addListener(FootstepsTrailNeoForgeClient::onRegisterKeyMappings);

        // Register Game Event Bus listeners
        NeoForge.EVENT_BUS.addListener(FootstepsTrailNeoForgeClient::onRegisterClientCommands);
        NeoForge.EVENT_BUS.addListener(FootstepsTrailNeoForgeClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(FootstepsTrailNeoForgeClient::onSubmitCustomGeometry);
    }

    /** Terrain footprints need a hook into whichever renderer builds chunk meshes. */
    private static void detectRenderer() {
        ClassLoader loader = FootstepsTrailNeoForgeClient.class.getClassLoader();
        RendererCompat.detect(new RendererCompat.Environment() {
            @Override public boolean modLoaded(String id) { return ModList.get().isLoaded(id); }
            @Override public boolean hasClass(String name) { return loader.getResource(name.replace('.', '/') + ".class") != null; }
            @Override public void load(String name) throws ClassNotFoundException { Class.forName(name, false, loader); }
        }, new RendererCompat.Hook("sodium", "Sodium",
                "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer"));
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        toggleKey = new KeyMapping(
                "key.footstepstrail.toggle",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_Z,
                KEY_CATEGORY);
        event.register(toggleKey);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (toggleKey != null && toggleKey.consumeClick()) {
            FootstepsConfig config = FootstepsConfig.getInstance();
            config.toggle();
            config.save();

            if (!config.isEnabled()) {
                FootstepsTracker.getInstance().clear();
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                String key = config.isEnabled()
                        ? "text.footstepstrail.toggle.enabled"
                        : "text.footstepstrail.toggle.disabled";
                mc.player.sendSystemMessage(Component.translatable(key));
            }
        }

        FootstepsConfig config = FootstepsConfig.getInstance();
        if (dev.footstepstrail.deformation.TerrainFootprints.tick(Minecraft.getInstance())) return;
        if (!config.isEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel world = mc.level;
        if (player == null || world == null) {
            return;
        }

        FootstepsTracker tracker = FootstepsTracker.getInstance();
        if (config.isStealthSneak() && player.isCrouching()) {
            // Stealth sneak: do not record footsteps when sneaking
        } else {
            Candidate candidate = getGroundCandidate(player, world);
            if (candidate != null) {
                // Sample the surface once, now: the block may be broken or its
                // chunk unloaded long before the mark finishes fading.
                BlockState state = world.getBlockState(candidate.blockPos);
                tracker.recordCandidate(candidate.pos.x, candidate.pos.y, candidate.pos.z,
                        player.getYRot(), world.dimension().identifier().toString(),
                        player.isSprinting(), config,
                        SurfaceSampler.profileFor(state),
                        SurfaceSampler.baseColorFor(state, candidate.blockPos, world),
                        candidate.blockPos.getX(), candidate.blockPos.getY(), candidate.blockPos.getZ());
            }
        }
        tracker.removeUnsupported(world.dimension().identifier().toString(),
                (x, y, z) -> supportsFootstep(world, x, y, z));
        tracker.removeExpiredFootsteps(config);
    }

    /** A footstep position paired with the block it landed on. */
    private static final class Candidate {
        final Vec3 pos;
        final BlockPos blockPos;

        Candidate(Vec3 pos, BlockPos blockPos) {
            this.pos = pos;
            this.blockPos = blockPos;
        }
    }


    private static Candidate getGroundCandidate(LocalPlayer player, ClientLevel world) {
        // Ground placement uses the visible outline rather than the collision
        // shape. Snow layers intentionally collide below their rendered top,
        // which otherwise puts the footprint inside the block underneath.
        if (!player.onGround()) {
            return null;
        }

        // Don't place footprints in/while touching fluids like water/lava.
        if (player.isInWater() || player.isUnderWater() || player.isInLava()) {
            return null;
        }

        Vec3 start = player.position().add(0.0, SURFACE_RAY_START_OFFSET, 0.0);
        Vec3 end = start.add(0.0, -2.0, 0.0);

        BlockHitResult hit = findSurfaceHit(player, world, start, end);

        if (hit == null) {
            return null;
        }

        Vec3 hitPos = hit.getLocation();
        return new Candidate(new Vec3(hitPos.x, hitPos.y, hitPos.z), hit.getBlockPos());
    }

    /**
     * Find the first outline that represents a floor, skipping outline-only
     * plants such as grass and flowers.
     */
    /**
     * Whether the block at this position is a floor a footstep can rest on. Unloaded
     * chunks count as supported so distant steps are not dropped before they can be checked.
     */
    private static boolean supportsFootstep(ClientLevel world, int x, int y, int z) {
        if (!world.hasChunk(x >> 4, z >> 4)) {
            return true;
        }
        BlockPos pos = new BlockPos(x, y, z);
        return isFloor(world, pos, world.getBlockState(pos));
    }

    /** A one-layer snow block has an empty collision shape by design, but its visible outline is still a floor. */
    private static boolean isFloor(ClientLevel world, BlockPos pos, BlockState state) {
        return state.is(net.minecraft.world.level.block.Blocks.SNOW)
                || !state.getCollisionShape(world, pos).isEmpty();
    }

    private static BlockHitResult findSurfaceHit(LocalPlayer player, ClientLevel world, Vec3 start, Vec3 end) {
        Vec3 rayStart = start;
        for (int attempts = 0; attempts < 16 && rayStart.y > end.y; attempts++) {
            BlockHitResult hit = world.clip(new ClipContext(
                    rayStart,
                    end,
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    player));

            if (hit.getType() != HitResult.Type.BLOCK) {
                return null;
            }

            BlockPos blockPos = hit.getBlockPos();
            BlockState state = world.getBlockState(blockPos);
            if (isFloor(world, blockPos, state)) {
                return hit;
            }

            // Continue below the entire decorative block so the same outline
            // cannot be hit again while the ray starts inside it.
            double nextY = blockPos.getY() - 1.0e-4;
            if (nextY <= end.y) {
                return null;
            }
            Vec3 hitPos = hit.getLocation();
            rayStart = new Vec3(hitPos.x, nextY, hitPos.z);
        }

        return null;
    }

    private static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;
            FootstepsRenderer.getInstance().render(event.getPoseStack(), cameraPos, event.getSubmitNodeCollector());
        }
    }

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        FootstepsCommand.register(event.getDispatcher());
    }
}
