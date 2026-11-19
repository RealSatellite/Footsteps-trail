package dev.footstepstrail.config;

/**
 * Shape of a {@link FootstepType#CLASSIC} footstep marker.
 */
public enum FootstepShape {
    /** Simple flat square (original). */
    SQUARE,

    /** Circle approximated with segmented quads. */
    CIRCLE,

    /** Paw print – one oval pad plus four small toe circles. */
    PAW,

    /** Cross / plus shape made of two overlapping rectangles. */
    CROSS
}
