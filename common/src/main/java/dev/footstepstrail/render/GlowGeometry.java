package dev.footstepstrail.render;

/**
 * Glow for classic footsteps: a soft radial halo drawn under the marker, plus a
 * brightened core colour. Footstep markers are already drawn unlit, so this
 * reads as glowing at night on every loader without a custom blend mode.
 */
public final class GlowGeometry {
    private static final int SEGMENTS = 20;
    private static final float HALO_SCALE = 2.0f;
    private static final float HALO_ALPHA = 0.45f;
    private static final float HALO_DROP = 0.002f;
    private static final float CORE_LIFT = 0.35f;

    private GlowGeometry() {
    }

    /**
     * Emit a halo as a quad fan whose alpha falls from the centre to zero at
     * the rim. Each quad repeats its last vertex, so it renders as a triangle.
     */
    public static void emitHalo(VertexSink sink, float x, float y, float z,
            float halfSize, int r, int g, int b, int a) {
        float radius = halfSize * HALO_SCALE;
        float hy = y - HALO_DROP;
        int centreAlpha = Math.round(a * HALO_ALPHA);
        if (centreAlpha <= 0) return;
        double step = 2.0 * Math.PI / SEGMENTS;
        for (int i = 0; i < SEGMENTS; i++) {
            float x1 = x + (float) Math.cos(i * step) * radius;
            float z1 = z + (float) Math.sin(i * step) * radius;
            float x2 = x + (float) Math.cos((i + 1) * step) * radius;
            float z2 = z + (float) Math.sin((i + 1) * step) * radius;
            sink.vertex(x, hy, z, r, g, b, centreAlpha);
            sink.vertex(x1, hy, z1, r, g, b, 0);
            sink.vertex(x2, hy, z2, r, g, b, 0);
            sink.vertex(x2, hy, z2, r, g, b, 0);
        }
    }

    /** Lift a 0-255 channel toward white so the core looks lit. */
    public static int brighten(int channel) {
        return Math.min(255, Math.round(channel + (255 - channel) * CORE_LIFT));
    }
}
