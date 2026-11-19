package dev.footstepstrail.config;

/**
 * Families of walkable surfaces, each describing how a footstep disturbs it.
 *
 * <p>
 * The constants here drive the procedural footprint geometry: how large the
 * imprint is, how strongly the depression darkens, how much material is pushed
 * up into a rim, how ragged the edge is, and how long the mark survives.
 * </p>
 */
public enum SurfaceProfile {

    /**
     * Sand, gravel, soul sand, concrete powder. Loose material collapses into a
     * displaced patch rather than holding a sole shape, so this profile is wide,
     * very irregular, and scatters loose grains around the impact.
     */
    LOOSE_GRANULAR(1.0f, 0.45f, 0.15f, 0.42f, 1.0f, 1.0f, 7, true),

    /**
     * Snow and powder snow. Holds crisp imprint.
     */
    SNOW(1.0f, 0.45f, 0.15f, 0.10f, 1.0f, 1.0f, 0, false),

    /**
     * Dirt, farmland, mud, clay. Compacts into a clean sole with a defined edge.
     */
    SOFT_EARTH(1.0f, 0.45f, 0.10f, 0.16f, 1.0f, 1.0f, 0, false),

    /**
     * Grass, podzol, moss, mycelium.
     */
    SOFT_ORGANIC(1.0f, 0.42f, 0.08f, 0.22f, 1.0f, 1.0f, 0, false),

    /**
     * Stone, planks, metal, and everything unrecognised.
     */
    HARD(1.0f, 0.35f, 0.08f, 0.25f, 1.0f, 1.0f, 0, false);

    private final float sizeScale;
    private final float coreDarkening;
    private final float rimBrightening;
    private final float edgeIrregularity;
    private final float opacityScale;
    private final float lifetimeScale;
    private final int grainCount;
    private final boolean patchLike;

    SurfaceProfile(float sizeScale, float coreDarkening, float rimBrightening,
            float edgeIrregularity, float opacityScale, float lifetimeScale,
            int grainCount, boolean patchLike) {
        this.sizeScale = sizeScale;
        this.coreDarkening = coreDarkening;
        this.rimBrightening = rimBrightening;
        this.edgeIrregularity = edgeIrregularity;
        this.opacityScale = opacityScale;
        this.lifetimeScale = lifetimeScale;
        this.grainCount = grainCount;
        this.patchLike = patchLike;
    }

    /** Multiplier applied to the configured marker size. */
    public float getSizeScale() {
        return sizeScale;
    }

    /** How much the depression darkens the block colour, 0..1. */
    public float getCoreDarkening() {
        return coreDarkening;
    }

    /** How much the pushed-up rim brightens the block colour, 0..1. */
    public float getRimBrightening() {
        return rimBrightening;
    }

    /** How ragged the outline is, 0 (smooth) .. 1 (very broken up). */
    public float getEdgeIrregularity() {
        return edgeIrregularity;
    }

    /** Multiplier on the final alpha, letting hard surfaces stay faint. */
    public float getOpacityScale() {
        return opacityScale;
    }

    /**
     * Multiplier on how long the mark lasts in TIME mode. Values below 1 fade
     * faster than configured, above 1 linger.
     */
    public float getLifetimeScale() {
        return lifetimeScale;
    }

    /** Number of scattered debris specks thrown around the imprint. */
    public int getGrainCount() {
        return grainCount;
    }

    /**
     * Whether the imprint reads as a collapsed patch instead of a sole
     * silhouette.
     */
    public boolean isPatchLike() {
        return patchLike;
    }
}
