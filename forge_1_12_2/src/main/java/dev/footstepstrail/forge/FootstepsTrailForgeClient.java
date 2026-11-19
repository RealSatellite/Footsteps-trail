package dev.footstepstrail.forge;

import dev.footstepstrail.FootstepsTrail;
import dev.footstepstrail.config.FootstepsConfig;
import dev.footstepstrail.deformation.RendererCompat;
import dev.footstepstrail.forge.command.FootstepsCommand;
import dev.footstepstrail.render.FootstepsRenderer;
import dev.footstepstrail.tracking.FootstepsTracker;
import dev.footstepstrail.render.SurfaceSampler;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * Forge 1.12.2 client-only hooks (keybind, tick tracking, rendering, command).
 */
public final class FootstepsTrailForgeClient {

    /** Clears the small gap between a player's collision feet and visible snow. */
    private static final double SURFACE_RAY_START_OFFSET = 0.25;

    private static KeyBinding toggleKey;
    private static boolean initialized;

    private FootstepsTrailForgeClient() {
    }

    /** Terrain footprints need the vanilla chunk renderer; replacements fall back to surface footprints. */
    private static void detectRenderer() {
        final ClassLoader loader = FootstepsTrailForgeClient.class.getClassLoader();
        RendererCompat.detect(new RendererCompat.Environment() {
            @Override public boolean modLoaded(String id) { return net.minecraftforge.fml.common.Loader.isModLoaded(id); }
            @Override public boolean hasClass(String name) { return loader.getResource(name.replace('.', '/') + ".class") != null; }
            @Override public void load(String name) throws ClassNotFoundException { Class.forName(name, false, loader); }
        });
    }

    public static void preInit() {
        // Load configuration early
        FootstepsConfig.getInstance();
        detectRenderer();

        toggleKey = new KeyBinding("key.footstepstrail.toggle", Keyboard.KEY_Z, "key.category.footstepstrail");
        ClientRegistry.registerKeyBinding(toggleKey);

        MinecraftForge.EVENT_BUS.register(new Handler());
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        FootstepsTrail.LOGGER.info("Initializing Footsteps Trail (Forge 1.12.2 client)");

        // Client-side command
        ClientCommandHandler.instance.registerCommand(new FootstepsCommand());
    }

    private static final class Handler {

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }

            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player == null || mc.world == null) {
                dev.footstepstrail.deformation.TerrainFootprints.tick(mc);
                return;
            }

            if (toggleKey != null && toggleKey.isPressed()) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.toggle();
                config.save();

                if (!config.isEnabled()) {
                    FootstepsTracker.getInstance().clear();
                }

                EntityPlayerSP player = mc.player;
                player.sendStatusMessage(
                        new TextComponentTranslation(config.isEnabled()
                                ? "text.footstepstrail.toggle.enabled"
                                : "text.footstepstrail.toggle.disabled"),
                        true
                );
            }

            FootstepsConfig config = FootstepsConfig.getInstance();
            if (dev.footstepstrail.deformation.TerrainFootprints.tick(mc)) return;
            if (!config.isEnabled()) {
                return;
            }

            FootstepsTracker tracker = FootstepsTracker.getInstance();
            if (config.isStealthSneak() && mc.player.isSneaking()) {
                // Stealth sneak: do not record footsteps when sneaking
            } else {
                Candidate candidate = getGroundCandidate(mc.player);
                if (candidate != null) {
                    IBlockState state = mc.world.getBlockState(candidate.blockPos);
                    tracker.recordCandidate(candidate.pos.x, candidate.pos.y, candidate.pos.z,
                            mc.player.rotationYaw, String.valueOf(mc.world.provider.getDimensionType().getName()),
                            mc.player.isSprinting(), config,
                            SurfaceSampler.profileFor(state),
                            SurfaceSampler.baseColorFor(state, candidate.blockPos, mc.world),
                            candidate.blockPos.getX(), candidate.blockPos.getY(), candidate.blockPos.getZ());
                }
            }
            World world = mc.world;
            tracker.removeUnsupported(String.valueOf(world.provider.getDimensionType().getName()),
                    (x, y, z) -> supportsFootstep(world, x, y, z));
            tracker.removeExpiredFootsteps(config);
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


        /**
         * Whether the block at this position is a floor a footstep can rest on. Unloaded
         * chunks count as supported so distant steps are not dropped before they can be checked.
         */
        private static boolean supportsFootstep(World world, int x, int y, int z) {
            if (world.getChunkProvider().getLoadedChunk(x >> 4, z >> 4) == null) {
                return true;
            }
            BlockPos pos = new BlockPos(x, y, z);
            return isFloor(world, pos, world.getBlockState(pos));
        }

        /** A one-layer snow block has no collision box by design, but its visible bounds are still a floor. */
        private static boolean isFloor(World world, BlockPos pos, IBlockState state) {
            if (state.getBlock() == net.minecraft.init.Blocks.SNOW_LAYER) {
                return true;
            }
            AxisAlignedBB box = state.getCollisionBoundingBox(world, pos);
            return box != null && box != Block.NULL_AABB;
        }

        private Candidate getGroundCandidate(EntityPlayerSP player) {
            // Ground placement uses the visible block bounds. Snow layers
            // intentionally collide below their rendered top, which otherwise
            // puts the footprint inside the block underneath.
            if (!player.onGround) {
                return null;
            }

            // Don't place footprints in/while touching fluids like water/lava.
            if (player.isInWater() || player.isInLava()) {
                return null;
            }

            Vec3d start = new Vec3d(player.posX, player.posY + SURFACE_RAY_START_OFFSET, player.posZ);
            Vec3d end = new Vec3d(player.posX, start.y - 2.0, player.posZ);

            // The legacy API uses the block's outline for this raycast. Passing
            // false for ignoreBlockWithoutBoundingBox is what lets thin snow
            // layers participate instead of skipping to the block below.
            for (int attempts = 0; attempts < 16 && start.y > end.y; attempts++) {
                RayTraceResult hit = player.world.rayTraceBlocks(start, end, false, false, false);
                if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
                    return null;
                }

                BlockPos blockPos = hit.getBlockPos();
                IBlockState state = player.world.getBlockState(blockPos);
                if (isFloor(player.world, blockPos, state)) {
                    Vec3d hitPos = hit.hitVec;
                    return new Candidate(new Vec3d(hitPos.x, hitPos.y, hitPos.z), blockPos);
                }

                // Continue below the entire decorative block so the same
                // outline cannot be hit again while the ray starts inside it.
                double nextY = blockPos.getY() - 1.0e-4;
                if (nextY <= end.y) {
                    return null;
                }
                start = new Vec3d(hit.hitVec.x, nextY, hit.hitVec.z);
            }

            return null;
        }

        @SubscribeEvent
        public void onRenderWorldLast(RenderWorldLastEvent event) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.world == null) {
                return;
            }

            FootstepsRenderer.getInstance().render(event.getPartialTicks());
        }
    }
}
