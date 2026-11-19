package dev.footstepstrail.deformation;

import dev.footstepstrail.config.SurfaceProfile;
import dev.footstepstrail.render.SurfaceSampler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.chunk.ChunkStatus;

/** Shared Fabric/Forge 1.20.1 world adapter (Yarn mappings). */
public final class TerrainFootprints {
    private static final TerrainContacts<BlockState> contacts = new TerrainContacts<>();
    private static WorldAccess world;

    public static boolean tick(MinecraftClient mc) {
        if (world == null || world.level != mc.world) world = mc.world == null ? null : new WorldAccess(mc.world);
        ClientPlayerEntity player = mc.player;
        return contacts.tick(world, player == null ? null : new TerrainContacts.Player(
                player.getX(), player.getY(), player.getZ(), player.bodyYaw,
                player.isOnGround() && !player.isTouchingWater() && !player.isInLava(), player.isSprinting(), player.isSneaking()));
    }

    public static TerrainContacts.Surface surface(BlockPos pos, BlockState state) {
        return contacts.surface(pos.getX(), pos.getY(), pos.getZ(), state);
    }

    public static void emitted() { contacts.emitted(); }

    private static final class WorldAccess implements TerrainContacts.World<BlockState> {
        private final ClientWorld level;
        WorldAccess(ClientWorld level) { this.level = level; }

        @Override public TerrainContacts.Hit floor(double x, double y, double z) {
            Vec3d start = new Vec3d(x, y, z), end = start.add(0, -1.6, 0);
            for (int i = 0; i < 12; i++) {
                BlockHitResult hit = level.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE,
                        RaycastContext.FluidHandling.NONE, MinecraftClient.getInstance().player));
                if (hit.getType() != HitResult.Type.BLOCK) return null;
                BlockPos pos = hit.getBlockPos();
                BlockState state = level.getBlockState(pos);
                if ((state.isOf(Blocks.SNOW) || !state.getCollisionShape(level, pos).isEmpty()) && hit.getSide() == Direction.UP)
                    return new TerrainContacts.Hit(pos.getX(), pos.getY(), pos.getZ(), hit.getPos().y);
                start = new Vec3d(x, pos.getY() - 0.001, z);
                if (start.y <= end.y) return null;
            }
            return null;
        }

        private BlockPos pos(TerrainContacts.Position p) { return new BlockPos(p.x, p.y, p.z); }
        @Override public BlockState state(TerrainContacts.Position p) { return level.getBlockState(pos(p)); }
        @Override public boolean loaded(TerrainContacts.Position p) {
            return level.getChunkManager().getChunk(p.x >> 4, p.z >> 4, ChunkStatus.FULL, false) != null;
        }
        @Override public String dimension() { return level.getRegistryKey().getValue().toString(); }
        @Override public SurfaceProfile profile(BlockState state) { return SurfaceSampler.profileFor(state); }
        @Override public int color(BlockState state, TerrainContacts.Position p) { return SurfaceSampler.baseColorFor(state, pos(p), level); }
        @Override public DeformationMaterial material(BlockState state, TerrainContacts.Position p) {
            return DeformationMaterial.resolve(MaterialSounds.blockId(state),
                    profile(state), MaterialSounds.family(state, level, pos(p)));
        }
        @Override public void particle(BlockState state, double x, double y, double z, double vx, double vy, double vz) {
            level.addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), x, y, z, vx, vy, vz);
        }
        @Override public void dirtySection(int x, int y, int z) {
            MinecraftClient.getInstance().worldRenderer.scheduleBlockRender(x, y, z);
        }
    }

    private TerrainFootprints() {}
}
