package dev.footstepstrail.config;

/**
 * The three kinds of footsteps the mod can leave.
 */
public enum FootstepType {
    /** Footprints pressed into the terrain mesh itself (default). */
    TERRAIN,

    /** Block-aware overlay: shape, tint and lifetime come from the surface walked on. */
    SURFACE,

    /** Flat markers with a configurable shape, colour and optional glow. */
    CLASSIC
}
