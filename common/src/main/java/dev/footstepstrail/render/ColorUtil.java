package dev.footstepstrail.render;

/**
 * Small colour helpers for the procedural footprints. All colours are packed
 * as 0xRRGGBB ints; alpha is handled separately by the vertex sinks.
 */
public final class ColorUtil {

    private ColorUtil() {
    }

    /**
     * Darken a colour for the depression core of a footprint.
     */
    public static int darken(int rgb, float amount) {
        amount = clamp01(amount);
        float r = (rgb >> 16 & 0xFF) / 255f;
        float g = (rgb >> 8 & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;

        float factor = 1.0f - amount;
        r *= factor;
        g *= factor;
        b *= factor;

        return pack(r, g, b);
    }

    /**
     * Brighten a colour for sunlit pushed-up material.
     */
    public static int lighten(int rgb, float amount) {
        amount = clamp01(amount);
        float r = (rgb >> 16 & 0xFF) / 255f;
        float g = (rgb >> 8 & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;

        float luma = 0.299f * r + 0.587f * g + 0.114f * b;
        float lit = Math.min(1f, luma * (1f + amount) + amount * 0.12f);

        r = lerp(r, lit, amount * 0.6f);
        g = lerp(g, lit, amount * 0.6f);
        b = lerp(b, lit, amount * 0.6f);

        return pack(r, g, b);
    }

    /** Pack 0..1 float channels into 0xRRGGBB. */
    public static int pack(float r, float g, float b) {
        int ri = (int) (clamp01(r) * 255f + 0.5f);
        int gi = (int) (clamp01(g) * 255f + 0.5f);
        int bi = (int) (clamp01(b) * 255f + 0.5f);
        return (ri << 16) | (gi << 8) | bi;
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
