package dev.footstepstrail.deformation;

import dev.footstepstrail.config.FootstepsConfig;
import dev.footstepstrail.config.SurfaceProfile;

/** Mechanical response of a surface, in blocks. No renderer dependencies. */
public enum DeformationMaterial {
    SAND(0.045, 0.32, 0.18, 0, 0.5, 0.25),
    GRAVEL(0.038, 0.28, 0.32, 0, 0.5, 0.25),
    SNOW(0.065, 0.22, 0.06, 0, 0.5, 0.25),
    EARTH(0.036, 0.18, 0.16, 0, 0.5, 0.25),
    MUD(0.048, 0.38, 0.06, 0, 0.5, 0.25),
    ORGANIC(0.036, 0.16, 0.20, 0, 0.5, 0.25),
    STONE(0.012, 0.015, 0.12, 0, 1, 0.25),
    WOOD(0.014, 0.04, 0.08, 0, 1, 0.25),
    METAL(0.007, 0.03, 0.02, 0, 1, 0.25),
    GLASS(0.006, 0.01, 0.02, 0, 1, 0.25),
    /** Slime and honey blocks: a deep, smooth, rounded squash with a soft rim. */
    GEL(0.07, 0.45, 0.01, 0.8, 0, 0);

    public final double depth;
    public final double rim;
    public final double roughness;
    /** 0 = crisp boot outline with tread; 1 = fully rounded outline, wide soft rim. */
    public final double softness;
    /**
     * Texels (possibly a fraction of one) a pixel print is cut into this surface; 0 keeps the
     * smooth, animated dent (slime and honey, whose springy recovery is the point).
     */
    public final double pixelDepth;
    /**
     * Texels the ground bordering a pixel print is pushed up; 0 for none. Kept far below a texel
     * so the edge reads as loose material, not as noise.
     */
    public final double pixelRim;

    DeformationMaterial(double depth, double rim, double roughness, double softness,
            double pixelDepth, double pixelRim) {
        this.depth = depth;
        this.rim = rim;
        this.roughness = roughness;
        this.softness = softness;
        this.pixelDepth = pixelDepth;
        this.pixelRim = pixelRim;
    }

    /** Whether the terrain renderer draws this surface's prints as pixel-grid cuts. */
    public boolean isPixel() {
        return pixelDepth > 0;
    }

    public boolean isHard() {
        return this == STONE || this == WOOD || this == METAL || this == GLASS;
    }

    /**
     * How much the floor of a smooth dent is darkened. Soft ground is deep enough for its walls
     * to show the dent, so the floor only gets a light tint; a strong uniform tint reads as
     * paint, not depth. In the terrain renderer only non-pixel surfaces (see {@link #isPixel()})
     * draw smooth dents.
     */
    public double cavityContrast() {
        return isHard() ? 0.32 : 0.06;
    }

    /** Config names, including SLIME and HONEY from before they shared one style. */
    public static DeformationMaterial byName(String name) {
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        if (upper.equals("SLIME") || upper.equals("HONEY")) return GEL;
        try {
            return valueOf(upper);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** Registry names and sound families are normalized by the version adapters. */
    public static DeformationMaterial resolve(String blockId, SurfaceProfile profile, DeformationMaterial sound) {
        DeformationMaterial override = FootstepsConfig.getInstance().deformationMaterial(blockId);
        if (override != null) return override;
        String id = blockId.substring(blockId.indexOf(':') + 1);
        if (id.contains("slime") || id.equals("honey_block")) return GEL;
        if (id.contains("gravel")) return GRAVEL;
        if (id.equals("mud") || id.equals("packed_mud") || id.equals("clay")) return MUD;
        if (id.endsWith("_ore") || id.contains("mud_brick")) return STONE;
        if (id.contains("glass") || id.contains("ice")) return GLASS;
        if (id.contains("iron") || id.contains("copper") || id.contains("gold") || id.contains("netherite")) return METAL;
        if (id.contains("planks") || id.contains("log") || id.contains("wood") || id.contains("bamboo") || id.contains("stem")) return WOOD;
        if (sound == WOOD || sound == METAL || sound == GLASS) return sound;
        if (id.contains("wool") || id.contains("carpet") || id.equals("hay_block")) return ORGANIC;
        switch (profile) {
            case LOOSE_GRANULAR: return SAND;
            case SNOW: return SNOW;
            case SOFT_EARTH: return EARTH;
            case SOFT_ORGANIC: return ORGANIC;
            default: return STONE;
        }
    }
}
