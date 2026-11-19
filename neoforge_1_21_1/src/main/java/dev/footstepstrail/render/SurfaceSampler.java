package dev.footstepstrail.render;

import dev.footstepstrail.config.SurfaceProfile;
import dev.footstepstrail.tracking.Footstep;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * Classifies the block a footstep landed on and samples its colour.
 *
 * <p>
 * Called once per recorded step rather than per frame: the block may be broken
 * or its chunk unloaded before the mark fades, and re-sampling hundreds of
 * footsteps every frame would be wasteful.
 * </p>
 */
public final class SurfaceSampler {

    private SurfaceSampler() {
    }

    /**
     * Decide which surface family a block belongs to.
     *
     * <p>
     * Vanilla blocks are matched directly, then tags catch correctly-tagged
     * modded blocks, then step sounds catch most of the rest. Anything
     * unrecognised is treated as a hard surface.
     * </p>
     */
    public static SurfaceProfile profileFor(BlockState state) {
        if (state == null || state.isAir()) {
            return SurfaceProfile.HARD;
        }

        if (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL)
                || state.is(Blocks.SUSPICIOUS_SAND) || state.is(Blocks.SUSPICIOUS_GRAVEL)) {
            return SurfaceProfile.LOOSE_GRANULAR;
        }

        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
            return SurfaceProfile.SNOW;
        }

        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM) || state.is(Blocks.MOSS_BLOCK)) {
            return SurfaceProfile.SOFT_ORGANIC;
        }

        if (state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.FARMLAND) || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.MUD) || state.is(Blocks.CLAY)) {
            return SurfaceProfile.SOFT_EARTH;
        }

        // Tag pass: modded blocks that tag themselves correctly work for free.
        if (state.is(BlockTags.SAND)) {
            return SurfaceProfile.LOOSE_GRANULAR;
        }
        if (state.is(BlockTags.SNOW)) {
            return SurfaceProfile.SNOW;
        }
        if (state.is(BlockTags.DIRT)) {
            return SurfaceProfile.SOFT_EARTH;
        }

        return fromSound(state);
    }

    /**
     * Fall back to the block's step sound, which is a good proxy for how loose
     * a surface is and covers most remaining modded blocks.
     */
    @SuppressWarnings("deprecation")
    private static SurfaceProfile fromSound(BlockState state) {
        SoundType sound;
        try {
            sound = state.getSoundType();
        } catch (Throwable ignored) {
            return SurfaceProfile.HARD;
        }

        if (sound == SoundType.SAND || sound == SoundType.GRAVEL) {
            return SurfaceProfile.LOOSE_GRANULAR;
        }
        if (sound == SoundType.SNOW || sound == SoundType.POWDER_SNOW) {
            return SurfaceProfile.SNOW;
        }
        if (sound == SoundType.GRASS || sound == SoundType.MOSS
                || sound == SoundType.WET_GRASS) {
            return SurfaceProfile.SOFT_ORGANIC;
        }
        if (sound == SoundType.MUD || sound == SoundType.ROOTED_DIRT
                || sound == SoundType.SOUL_SAND || sound == SoundType.SOUL_SOIL) {
            return SurfaceProfile.SOFT_EARTH;
        }

        return SurfaceProfile.HARD;
    }

    /**
     * Sample the block's on-screen colour, packed as 0xRRGGBB.
     *
     * <p>
     * The block-colour path is tried first so biome tinting is respected: swamp
     * grass leaves a different mark than plains grass. Blocks without a tint
     * fall back to their map colour.
     * </p>
     */
    public static int baseColorFor(BlockState state, BlockPos pos, BlockGetter level) {
        if (state == null || state.isAir()) {
            return Footstep.DEFAULT_COLOR;
        }

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getBlockColors() != null && mc.level != null) {
                int tint = mc.getBlockColors().getColor(state, mc.level, pos, 0);
                if (tint != -1) {
                    return tint & 0xFFFFFF;
                }
            }
        } catch (Throwable ignored) {
            // Some blocks have no colour provider; fall through to map colour.
        }

        try {
            MapColor mapColor = state.getMapColor(level, pos);
            if (mapColor != null && mapColor != MapColor.NONE) {
                return mapColor.col & 0xFFFFFF;
            }
        } catch (Throwable ignored) {
            // Fall through to the neutral default.
        }

        return Footstep.DEFAULT_COLOR;
    }
}
