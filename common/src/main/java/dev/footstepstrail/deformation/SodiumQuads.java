package dev.footstepstrail.deformation;

/**
 * Colour conversion for Sodium-family chunk renderers. Their pipelines keep per-vertex
 * brightness (ambient occlusion and face shading) apart from the tint; terrain footprints bake
 * it into the colour like the vanilla adapter does, then hand the renderer full brightness.
 */
public final class SodiumQuads {
    /** ARGB tint times brightness, opaque. */
    public static int shadeArgb(int argb, float brightness) {
        int r = scale((argb >>> 16) & 255, brightness);
        int g = scale((argb >>> 8) & 255, brightness);
        int b = scale(argb & 255, brightness);
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    /** ABGR (Sodium's vertex order) tint times brightness, returned as opaque ARGB. */
    public static int abgrToShadedArgb(int abgr, float brightness) {
        return shadeArgb(swapRedBlue(abgr), brightness);
    }

    /** ARGB to ABGR or back: swaps the red and blue channels. */
    public static int swapRedBlue(int color) {
        return (color & 0xff00ff00) | ((color >>> 16) & 255) | ((color & 255) << 16);
    }

    private static int scale(int channel, float brightness) {
        int value = Math.round(channel * brightness);
        return value < 0 ? 0 : Math.min(255, value);
    }

    private SodiumQuads() {}
}
