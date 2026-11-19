package dev.footstepstrail.deformation;

import dev.footstepstrail.config.SurfaceProfile;
import dev.footstepstrail.render.SurfaceSampler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Minecraft 1.21.1 world adapter. Contact mechanics and meshing are shared with the other targets. */
public final class TerrainFootprints {
    private static final TerrainContacts<BlockState> contacts = new TerrainContacts<>();
    private static WorldAccess world;

    public static boolean tick(Minecraft mc) {
        if (world == null || world.level != mc.level) world = mc.level == null ? null : new WorldAccess(mc.level);
        LocalPlayer player = mc.player;
        return contacts.tick(world, player == null ? null : new TerrainContacts.Player(
                player.getX(), player.getY(), player.getZ(), player.yBodyRot,
                player.onGround() && !player.isInWater() && !player.isInLava(), player.isSprinting(), player.isCrouching()));
    }

    public static TerrainContacts.Surface surface(BlockPos pos, BlockState state) {
        return contacts.surface(pos.getX(), pos.getY(), pos.getZ(), state);
    }

    public static void emitted() { contacts.emitted(); }

    private static final class WorldAccess implements TerrainContacts.World<BlockState> {
        private final ClientLevel level;
        WorldAccess(ClientLevel level) { this.level = level; }

        @Override public TerrainContacts.Hit floor(double x, double y, double z) {
            Vec3 start = new Vec3(x, y, z), end = start.add(0, -1.6, 0);
            for (int i = 0; i < 12; i++) {
                BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE, Minecraft.getInstance().player));
                if (hit.getType() != HitResult.Type.BLOCK) return null;
                BlockPos pos = hit.getBlockPos();
                BlockState state = level.getBlockState(pos);
                if ((state.is(Blocks.SNOW) || !state.getCollisionShape(level, pos).isEmpty()) && hit.getDirection() == Direction.UP)
                    return new TerrainContacts.Hit(pos.getX(), pos.getY(), pos.getZ(), hit.getLocation().y);
                start = new Vec3(x, pos.getY() - 0.001, z);
                if (start.y <= end.y) return null;
            }
            return null;
        }

        private BlockPos pos(TerrainContacts.Position p) { return new BlockPos(p.x, p.y, p.z); }
        @Override public BlockState state(TerrainContacts.Position p) { return level.getBlockState(pos(p)); }
        @Override public boolean loaded(TerrainContacts.Position p) { return level.hasChunk(p.x >> 4, p.z >> 4); }
        @Override public String dimension() { return level.dimension().location().toString(); }
        @Override public SurfaceProfile profile(BlockState state) { return SurfaceSampler.profileFor(state); }
        @Override public int color(BlockState state, TerrainContacts.Position p) { return SurfaceSampler.baseColorFor(state, pos(p), level); }
        @Override public DeformationMaterial material(BlockState state, TerrainContacts.Position p) {
            return DeformationMaterial.resolve(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                    profile(state), MaterialSounds.family(state, level, pos(p)));
        }
        @Override public void particle(BlockState state, double x, double y, double z, double vx, double vy, double vz) {
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), x, y, z, vx, vy, vz);
        }
        @Override public void dirtySection(int x, int y, int z) {
            Minecraft.getInstance().levelRenderer.setSectionDirty(x, y, z);
        }
    }

    private TerrainFootprints() {}
}
