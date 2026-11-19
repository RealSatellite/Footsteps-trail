package dev.footstepstrail.render;

import dev.footstepstrail.config.SurfaceProfile;
import dev.footstepstrail.tracking.Footstep;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDirt;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Classifies the block a footstep landed on and samples its colour (Forge 1.12.2 MCP).
 */
public final class SurfaceSampler {

    private SurfaceSampler() {
    }

    /**
     * Decide which surface family a block belongs to.
     */
    public static SurfaceProfile profileFor(IBlockState state) {
        if (state == null || state.getBlock() == Blocks.AIR) {
            return SurfaceProfile.HARD;
        }

        Block block = state.getBlock();

        if (block == Blocks.SAND || block == Blocks.GRAVEL || block == Blocks.SOUL_SAND) {
            return SurfaceProfile.LOOSE_GRANULAR;
        }

        if (block == Blocks.SNOW || block == Blocks.SNOW_LAYER) {
            return SurfaceProfile.SNOW;
        }

        if (block == Blocks.GRASS || block == Blocks.MYCELIUM
                || (block == Blocks.DIRT && state.getValue(BlockDirt.VARIANT) == BlockDirt.DirtType.PODZOL)) {
            return SurfaceProfile.SOFT_ORGANIC;
        }

        if (block == Blocks.DIRT || block == Blocks.FARMLAND || block == Blocks.GRASS_PATH
                || block == Blocks.CLAY) {
            return SurfaceProfile.SOFT_EARTH;
        }

        return fromSound(state);
    }

    private static SurfaceProfile fromSound(IBlockState state) {
        SoundType sound;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.world == null) return SurfaceProfile.HARD;
            BlockPos pos = mc.player == null ? BlockPos.ORIGIN : mc.player.getPosition();
            sound = state.getBlock().getSoundType(state, mc.world, pos, mc.player);
        } catch (Throwable ignored) {
            return SurfaceProfile.HARD;
        }

        if (sound == SoundType.SAND) {
            return SurfaceProfile.LOOSE_GRANULAR;
        }
        if (sound == SoundType.SNOW) {
            return SurfaceProfile.SNOW;
        }
        if (sound == SoundType.PLANT) {
            return SurfaceProfile.SOFT_ORGANIC;
        }
        if (sound == SoundType.GROUND) {
            return SurfaceProfile.SOFT_EARTH;
        }

        return SurfaceProfile.HARD;
    }

    /**
     * Sample the block's on-screen colour, packed as 0xRRGGBB.
     */
    public static int baseColorFor(IBlockState state, BlockPos pos, IBlockAccess level) {
        if (state == null || state.getBlock() == Blocks.AIR) {
            return Footstep.DEFAULT_COLOR;
        }

        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.getBlockColors() != null && mc.world != null) {
                int tint = mc.getBlockColors().colorMultiplier(state, mc.world, pos, 0);
                if (tint != -1) {
                    return tint & 0xFFFFFF;
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            MapColor mapColor = state.getMapColor(level, pos);
            if (mapColor != null && mapColor != MapColor.AIR) {
                return mapColor.colorValue & 0xFFFFFF;
            }
        } catch (Throwable ignored) {
        }

        return Footstep.DEFAULT_COLOR;
    }
}
