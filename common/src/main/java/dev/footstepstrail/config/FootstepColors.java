package dev.footstepstrail.config;

/**
 * The preset colours offered by the settings screen.
 */
public final class FootstepColors {

    public static final String[] NAMES = { "white", "gray", "red", "green", "blue", "yellow" };

    private static final float[][] VALUES = {
            { 1.0f, 1.0f, 1.0f },
            { 0.7f, 0.7f, 0.7f },
            { 1.0f, 0.2f, 0.2f },
            { 0.2f, 1.0f, 0.2f },
            { 0.2f, 0.4f, 1.0f },
            { 1.0f, 1.0f, 0.2f },
    };

    private FootstepColors() {
    }

    /** Index of the preset matching the config's colour, or -1 for a custom colour. */
    public static int indexOf(FootstepsConfig config) {
        for (int i = 0; i < VALUES.length; i++) {
            if (close(VALUES[i][0], config.getColorR())
                    && close(VALUES[i][1], config.getColorG())
                    && close(VALUES[i][2], config.getColorB())) {
                return i;
            }
        }
        return -1;
    }

    /** Switch to the next preset (a custom colour moves to the first one). */
    public static void cycle(FootstepsConfig config) {
        int next = (indexOf(config) + 1) % VALUES.length;
        config.setColor(VALUES[next][0], VALUES[next][1], VALUES[next][2]);
    }

    private static boolean close(float a, float b) {
        return Math.abs(a - b) < 0.01f;
    }
}
