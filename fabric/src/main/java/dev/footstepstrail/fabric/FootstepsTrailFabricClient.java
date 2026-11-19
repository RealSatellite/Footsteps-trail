package dev.footstepstrail.fabric;

import dev.footstepstrail.FootstepsTrail;
import dev.footstepstrail.fabric.command.FootstepsCommand;
import dev.footstepstrail.config.FootstepsConfig;
import dev.footstepstrail.deformation.RendererCompat;
import net.fabricmc.loader.api.FabricLoader;
import dev.footstepstrail.render.FootstepsRenderer;
import dev.footstepstrail.tracking.FootstepsTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import dev.footstepstrail.render.SurfaceSampler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Fabric client-side entrypoint for the Footsteps Trail mod.
 */
public class FootstepsTrailFabricClient implements ClientModInitializer {

    /** Clears the small gap between a player's collision feet and visible snow. */
    private static final double SURFACE_RAY_START_OFFSET = 0.25;

    private static KeyBinding toggleKey;

    /** Terrain footprints need a hook into whichever renderer builds chunk meshes. */
    private static void detectRenderer() {
        ClassLoader loader = FootstepsTrailFabricClient.class.getClassLoader();
        RendererCompat.detect(new RendererCompat.Environment() {
            @Override public boolean modLoaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }
            @Override public boolean hasClass(String name) { return loader.getResource(name.replace('.', '/') + ".class") != null; }
            @Override public void load(String name) throws ClassNotFoundException { Class.forName(name, false, loader); }
        }, new RendererCompat.Hook("sodium", "Sodium",
                "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer"));
        // Indium meshes Fabric Renderer API models (Continuity, CTM resource packs) next to Sodium.
        if (FabricLoader.getInstance().isModLoaded("indium")) {
            String target = "link.infra.indium.renderer.render.TerrainRenderContext";
            try {
                Class.forName(target, false, loader);
            } catch (Throwable ignored) {
                // Reported below.
            }
            if (!RendererCompat.isAttached(target)) {
                FootstepsTrail.LOGGER.warning("Footsteps Trail: this version of Indium is not supported; "
                        + "terrain footprints will not show on blocks with connected or random textures.");
            }
        }
    }

    @Override
    public void onInitializeClient() {
        FootstepsTrail.LOGGER.info("Initializing Footsteps Trail (Fabric)");

        // Initialize configuration
        FootstepsConfig.getInstance();
        detectRenderer();

        // Register keybinding
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.footstepstrail.toggle",
                InputUtil.Type.KEYSYM,
                InputUtil.GLFW_KEY_Z,
            "key.category.footstepstrail"
        ));

        // Register client tick event for tracking updates
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            FootstepsConfig config = FootstepsConfig.getInstance();

            if (toggleKey.wasPressed()) {
                config.toggle();
                config.save();

                if (!config.isEnabled()) {
                    FootstepsTracker.getInstance().clear();
                }

                if (client.player != null) {
                    String key = config.isEnabled() ? "text.footstepstrail.toggle.enabled" : "text.footstepstrail.toggle.disabled";
                    client.player.sendMessage(Text.translatable(key), true);
                }
            }

            if (dev.footstepstrail.deformation.TerrainFootprints.tick(client)) return;
            if (!config.isEnabled()) {
                return;
            }

            ClientPlayerEntity player = client.player;
            ClientWorld world = client.world;
            if (player == null || world == null) {
                return;
            }

            FootstepsTracker tracker = FootstepsTracker.getInstance();
            if (config.isStealthSneak() && player.isSneaking()) {
                // Stealth sneak: do not record footsteps when sneaking
            } else {
                Candidate candidate = getGroundCandidate(player, world);
                if (candidate != null) {
                    BlockState state = world.getBlockState(candidate.blockPos);
                    tracker.recordCandidate(candidate.pos.x, candidate.pos.y, candidate.pos.z,
                            player.getYaw(), world.getRegistryKey().getValue().toString(),
                            player.isSprinting(), config,
                            SurfaceSampler.profileFor(state),
                            SurfaceSampler.baseColorFor(state, candidate.blockPos, world),
                            candidate.blockPos.getX(), candidate.blockPos.getY(), candidate.blockPos.getZ());
                }
            }
            tracker.removeUnsupported(world.getRegistryKey().getValue().toString(),
                    (x, y, z) -> supportsFootstep(world, x, y, z));
            tracker.removeExpiredFootsteps(config);
        });

        // Register world render event for drawing footsteps
        WorldRenderEvents.LAST.register(context -> {
            FootstepsRenderer.getInstance().render(context.matrixStack(), context.camera());
        });

        // Register commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            FootstepsCommand.register(dispatcher, registryAccess);
        });

        FootstepsTrail.LOGGER.info("Footsteps Trail (Fabric) initialized successfully");
    }

    /** A footstep position paired with the block it landed on. */
    private static final class Candidate {
        final Vec3d pos;
        final BlockPos blockPos;

        Candidate(Vec3d pos, BlockPos blockPos) {
            this.pos = pos;
            this.blockPos = blockPos;
        }
    }


    private static Candidate getGroundCandidate(ClientPlayerEntity player, ClientWorld world) {
        // Ground placement uses the visible outline rather than the collision
        // shape. Snow layers intentionally collide below their rendered top,
        // which otherwise puts the footprint inside the block underneath.
        if (!player.isOnGround()) {
            return null;
        }

        // Don't place footprints in/while touching fluids like water/lava.
        if (player.isTouchingWater() || player.isSubmergedInWater() || player.isInLava()) {
            return null;
        }

        Vec3d start = player.getPos().add(0.0, SURFACE_RAY_START_OFFSET, 0.0);
        Vec3d end = start.add(0.0, -2.0, 0.0);

        BlockHitResult hit = findSurfaceHit(player, world, start, end);

        if (hit == null) {
            return null;
        }

        Vec3d hitPos = hit.getPos();
        return new Candidate(new Vec3d(hitPos.x, hitPos.y, hitPos.z), hit.getBlockPos());
    }

    /**
     * Find the first outline that represents a floor, skipping outline-only
     * plants such as grass and flowers.
     */
    /**
     * Whether the block at this position is a floor a footstep can rest on. Unloaded
     * chunks count as supported so distant steps are not dropped before they can be checked.
     */
    private static boolean supportsFootstep(ClientWorld world, int x, int y, int z) {
        if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) {
            return true;
        }
        BlockPos pos = new BlockPos(x, y, z);
        return isFloor(world, pos, world.getBlockState(pos));
    }

    /** A one-layer snow block has an empty collision shape by design, but its visible outline is still a floor. */
    private static boolean isFloor(ClientWorld world, BlockPos pos, BlockState state) {
        return state.isOf(Blocks.SNOW) || !state.getCollisionShape(world, pos).isEmpty();
    }

    private static BlockHitResult findSurfaceHit(ClientPlayerEntity player, ClientWorld world,
            Vec3d start, Vec3d end) {
        Vec3d rayStart = start;
        for (int attempts = 0; attempts < 16 && rayStart.y > end.y; attempts++) {
            HitResult result = world.raycast(new RaycastContext(
                    rayStart,
                    end,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    player));

            if (result.getType() != HitResult.Type.BLOCK) {
                return null;
            }
            BlockHitResult hit = (BlockHitResult) result;

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
            Vec3d hitPos = hit.getPos();
            rayStart = new Vec3d(hitPos.x, nextY, hitPos.z);
        }

        return null;
    }
}
