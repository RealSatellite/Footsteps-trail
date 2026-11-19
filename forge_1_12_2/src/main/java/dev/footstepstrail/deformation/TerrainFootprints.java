package dev.footstepstrail.deformation;

import dev.footstepstrail.config.SurfaceProfile;
import dev.footstepstrail.render.SurfaceSampler;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

/** Java 8 / Forge 1.12.2 world adapter for the shared contact engine. */
public final class TerrainFootprints {
    private static final TerrainContacts<IBlockState> contacts = new TerrainContacts<>();
    private static WorldAccess world;

    public static boolean tick(Minecraft mc) {
        TerrainBlockRenderer.install(mc);
        if (world == null || world.level != mc.world) world = mc.world == null ? null : new WorldAccess(mc.world);
        EntityPlayerSP player = mc.player;
        return contacts.tick(world, player == null ? null : new TerrainContacts.Player(
                player.posX, player.posY, player.posZ, player.renderYawOffset,
                player.onGround && !player.isInWater() && !player.isInLava(), player.isSprinting(), player.isSneaking()));
    }

    static TerrainContacts.Surface surface(BlockPos pos, IBlockState state) {
        return contacts.surface(pos.getX(), pos.getY(), pos.getZ(), state);
    }

    static void emitted() { contacts.emitted(); }

    private static final class WorldAccess implements TerrainContacts.World<IBlockState> {
        private final WorldClient level;
        WorldAccess(WorldClient level) { this.level = level; }

        @Override public TerrainContacts.Hit floor(double x, double y, double z) {
            Vec3d start = new Vec3d(x, y, z), end = new Vec3d(x, y - 1.6, z);
            for (int i = 0; i < 12; i++) {
                RayTraceResult hit = level.rayTraceBlocks(start, end, false, false, false);
                if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) return null;
                BlockPos pos = hit.getBlockPos();
                IBlockState state = level.getBlockState(pos);
                if ((state.getBlock() == Blocks.SNOW_LAYER || state.getCollisionBoundingBox(level, pos) != Block.NULL_AABB)
                        && hit.sideHit == EnumFacing.UP)
                    return new TerrainContacts.Hit(pos.getX(), pos.getY(), pos.getZ(), hit.hitVec.y);
                start = new Vec3d(x, pos.getY() - 0.001, z);
                if (start.y <= end.y) return null;
            }
            return null;
        }

        private BlockPos pos(TerrainContacts.Position p) { return new BlockPos(p.x, p.y, p.z); }
        @Override public IBlockState state(TerrainContacts.Position p) { return level.getBlockState(pos(p)); }
        @Override public boolean loaded(TerrainContacts.Position p) { return level.getChunkProvider().getLoadedChunk(p.x >> 4, p.z >> 4) != null; }
        @Override public String dimension() { return level.provider.getDimensionType().getName(); }
        @Override public SurfaceProfile profile(IBlockState state) { return SurfaceSampler.profileFor(state); }
        @Override public int color(IBlockState state, TerrainContacts.Position p) { return SurfaceSampler.baseColorFor(state, pos(p), level); }
        @Override public DeformationMaterial material(IBlockState state, TerrainContacts.Position p) {
            SoundType sound = state.getBlock().getSoundType(state, level, pos(p), Minecraft.getMinecraft().player);
            DeformationMaterial family = sound == SoundType.WOOD ? DeformationMaterial.WOOD
                    : sound == SoundType.METAL ? DeformationMaterial.METAL
                    : sound == SoundType.GLASS ? DeformationMaterial.GLASS : null;
            return DeformationMaterial.resolve(Block.REGISTRY.getNameForObject(state.getBlock()).toString(), profile(state), family);
        }
        @Override public void particle(IBlockState state, double x, double y, double z, double vx, double vy, double vz) {
            level.spawnParticle(EnumParticleTypes.BLOCK_DUST, x, y, z, vx, vy, vz, Block.getStateId(state));
        }
        @Override public void dirtySection(int x, int y, int z) {
            LegacyTerrainRebuilds.queue(Minecraft.getMinecraft(), x, y, z);
        }
    }

    private TerrainFootprints() {}
}
