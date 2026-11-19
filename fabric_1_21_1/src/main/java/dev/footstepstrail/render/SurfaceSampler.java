package dev.footstepstrail.render;

import dev.footstepstrail.config.SurfaceProfile;
import dev.footstepstrail.tracking.Footstep;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

/**
 * Classifies the block a footstep landed on and samples its colour (Fabric 1.20.1 Yarn).
 */
public final class SurfaceSampler {

    private SurfaceSampler() {
    }

    /**
     * Decide which surface family a block belongs to.
     */
    public static SurfaceProfile profileFor(BlockState state) {
        if (state == null || state.isAir()) {
            return SurfaceProfile.HARD;
        }

        if (state.isOf(Blocks.SAND) || state.isOf(Blocks.RED_SAND)
                || state.isOf(Blocks.GRAVEL)
                || state.isOf(Blocks.SOUL_SAND) || state.isOf(Blocks.SOUL_SOIL)
                || state.isOf(Blocks.SUSPICIOUS_SAND) || state.isOf(Blocks.SUSPICIOUS_GRAVEL)) {
            return SurfaceProfile.LOOSE_GRANULAR;
        }

        if (state.isOf(Blocks.SNOW) || state.isOf(Blocks.SNOW_BLOCK) || state.isOf(Blocks.POWDER_SNOW)) {
            return SurfaceProfile.SNOW;
        }

        if (state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.PODZOL)
                || state.isOf(Blocks.MYCELIUM) || state.isOf(Blocks.MOSS_BLOCK)) {
            return SurfaceProfile.SOFT_ORGANIC;
        }

        if (state.isOf(Blocks.DIRT) || state.isOf(Blocks.COARSE_DIRT) || state.isOf(Blocks.ROOTED_DIRT)
                || state.isOf(Blocks.FARMLAND) || state.isOf(Blocks.DIRT_PATH)
                || state.isOf(Blocks.MUD) || state.isOf(Blocks.CLAY)) {
            return SurfaceProfile.SOFT_EARTH;
        }

        if (state.isIn(BlockTags.SAND)) {
            return SurfaceProfile.LOOSE_GRANULAR;
        }
        if (state.isIn(BlockTags.SNOW)) {
            return SurfaceProfile.SNOW;
        }
        if (state.isIn(BlockTags.DIRT)) {
            return SurfaceProfile.SOFT_EARTH;
        }

        return fromSound(state);
    }

    private static SurfaceProfile fromSound(BlockState state) {
        BlockSoundGroup sound;
        try {
            sound = state.getSoundGroup();
        } catch (Throwable ignored) {
            return SurfaceProfile.HARD;
        }

        if (sound == BlockSoundGroup.SAND || sound == BlockSoundGroup.GRAVEL) {
            return SurfaceProfile.LOOSE_GRANULAR;
        }
        if (sound == BlockSoundGroup.SNOW || sound == BlockSoundGroup.POWDER_SNOW) {
            return SurfaceProfile.SNOW;
        }
        if (sound == BlockSoundGroup.GRASS || sound == BlockSoundGroup.MOSS_BLOCK
                || sound == BlockSoundGroup.WET_GRASS) {
            return SurfaceProfile.SOFT_ORGANIC;
        }
        if (sound == BlockSoundGroup.MUD || sound == BlockSoundGroup.ROOTED_DIRT
                || sound == BlockSoundGroup.SOUL_SAND || sound == BlockSoundGroup.SOUL_SOIL) {
            return SurfaceProfile.SOFT_EARTH;
        }

        return SurfaceProfile.HARD;
    }

    /**
     * Sample the block's on-screen colour, packed as 0xRRGGBB.
     */
    public static int baseColorFor(BlockState state, BlockPos pos, BlockView level) {
        if (state == null || state.isAir()) {
            return Footstep.DEFAULT_COLOR;
        }

        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.getBlockColors() != null && mc.world != null) {
                int tint = mc.getBlockColors().getColor(state, mc.world, pos, 0);
                if (tint != -1) {
                    return tint & 0xFFFFFF;
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            MapColor mapColor = state.getMapColor(level, pos);
            if (mapColor != null && mapColor != MapColor.CLEAR) {
                return mapColor.color & 0xFFFFFF;
            }
        } catch (Throwable ignored) {
        }

        return Footstep.DEFAULT_COLOR;
    }
}
