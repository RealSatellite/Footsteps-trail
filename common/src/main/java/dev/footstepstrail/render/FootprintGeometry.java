package dev.footstepstrail.render;

import dev.footstepstrail.config.SurfaceProfile;

/**
 * Builds the procedural geometry for a realistic footprint.
 *
 * <p>
 * A print is emitted as two stacked passes: a darkened core (the depression)
 * and a slightly larger, brighter rim ring underneath it (material pushed up
 * at the edges). That pairing is what makes the mark read as a dent in the
 * ground rather than a sticker laid on top of it.
 * </p>
 *
 * <p>
 * Every vertex is emitted as part of a quad, matching the QUADS draw mode the
 * renderers already use. Triangular fan slices repeat their last vertex to
 * form a degenerate quad, the same trick the existing circle drawing uses.
 * </p>
 */
public final class FootprintGeometry {

    /** Vertices around the outline of a sole or patch. */
    private static final int OUTLINE_STEPS = 22;

    /** Vertical gap between the rim pass and the core pass. */
    private static final float LAYER_GAP = 0.006f;

    private FootprintGeometry() {
    }

    /**
     * Emit one complete footprint.
     *
     * @param sink      receives the generated vertices
     * @param cx        print centre X, camera-relative
     * @param cy        ground height, camera-relative
     * @param cz        print centre Z, camera-relative
     * @param halfSize  configured marker half-size, before profile scaling
     * @param cos       cosine of the print's facing rotation
     * @param sin       sine of the print's facing rotation
     * @param profile   the surface being walked on
     * @param baseColor the sampled block colour, 0xRRGGBB
     * @param alpha     final alpha, 0..255
     * @param stepIndex step counter, used to vary and mirror successive prints
     */
    public static void emit(VertexSink sink, float cx, float cy, float cz,
            float halfSize, float cos, float sin,
            SurfaceProfile profile, int baseColor, int alpha, int stepIndex) {

        if (alpha <= 0) {
            return;
        }

        float size = halfSize * profile.getSizeScale();
        boolean mirrored = (stepIndex & 1) == 0;

        // Deterministic per-print variation: stable across frames, no allocation.
        int seed = hash(stepIndex);
        float scaleJitter = 0.9f + 0.2f * unit(seed);
        size *= scaleJitter;

        int coreColor = ColorUtil.darken(baseColor, profile.getCoreDarkening());
        int rimColor = ColorUtil.lighten(baseColor, profile.getRimBrightening());

        // Rim first and slightly lower, so the core reads as sunk into it.
        if (profile.getRimBrightening() > 0.01f) {
            emitOutline(sink, cx, cy, cz, size * 1.16f, cos, sin,
                    profile, rimColor, (int) (alpha * 0.75f), seed, mirrored);
        }

        emitOutline(sink, cx, cy + LAYER_GAP, cz, size, cos, sin,
                profile, coreColor, alpha, seed, mirrored);

        int grains = profile.getGrainCount();
        if (grains > 0) {
            emitGrains(sink, cx, cy + LAYER_GAP * 0.5f, cz, size, cos, sin,
                    grains, rimColor, (int) (alpha * 0.8f), seed);
        }
    }

    /**
     * Emit a filled outline as a fan of quads around the print centre.
     */
    private static void emitOutline(VertexSink sink, float cx, float cy, float cz,
            float size, float cos, float sin, SurfaceProfile profile,
            int color, int alpha, int seed, boolean mirrored) {

        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;

        float irregularity = profile.getEdgeIrregularity();
        boolean patch = profile.isPatchLike();

        float prevX = 0f;
        float prevZ = 0f;

        for (int i = 0; i <= OUTLINE_STEPS; i++) {
            float t = (float) i / OUTLINE_STEPS;
            float angle = (float) (t * Math.PI * 2.0);

            float radius = patch
                    ? patchRadius(angle, seed)
                    : soleRadius(angle);

            // Break up the edge so no two prints share a silhouette.
            float noise = unit(hash(seed * 31 + i)) - 0.5f;
            radius *= 1f + noise * irregularity;

            float localX = (float) Math.cos(angle) * radius * size;
            float localZ = (float) Math.sin(angle) * radius * size;

            if (mirrored) {
                localX = -localX;
            }

            float wx = localX * cos - localZ * sin;
            float wz = localX * sin + localZ * cos;

            if (i > 0) {
                sink.vertex(cx, cy, cz, r, g, b, alpha);
                sink.vertex(cx + prevX, cy, cz + prevZ, r, g, b, alpha);
                sink.vertex(cx + wx, cy, cz + wz, r, g, b, alpha);
                // Repeat to close the quad (fan slices are triangles).
                sink.vertex(cx + wx, cy, cz + wz, r, g, b, alpha);
            }

            prevX = wx;
            prevZ = wz;
        }
    }

    /**
     * Radius of a foot-shaped sole at the given angle.
     *
     * <p>
     * Angle 0 points along +X. The shape is a wide ball and toe region tapering
     * to a narrower heel, with a slight arch pinch on one side.
     * </p>
     */
    private static float soleRadius(float angle) {
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);

        // Longitudinal profile: toes (+) are broader than the heel (-).
        float lengthwise = c >= 0f ? 1.0f : 0.82f;

        // Narrow the print across its width to read as a foot, not a circle.
        float width = 0.58f;

        float base = (float) Math.sqrt(
                (c * lengthwise) * (c * lengthwise) + (s * width) * (s * width));
        if (base < 1.0e-4f) {
            return 0.5f;
        }

        float radius = 1f / base * 0.62f;

        // Arch: pinch the inner mid-foot slightly.
        float arch = 1f - 0.13f * (float) Math.exp(-Math.pow((angle - Math.PI * 1.5) * 1.7, 2));
        return radius * arch;
    }

    /**
     * Radius of a collapsed loose-material patch. Lower frequency lobes than a
     * sole, giving a rounded but clearly irregular displaced area.
     */
    private static float patchRadius(float angle, int seed) {
        float wobble = (float) (
                Math.sin(angle * 3.0 + unit(seed) * 6.28) * 0.13
                        + Math.sin(angle * 5.0 + unit(hash(seed)) * 6.28) * 0.07);
        return 0.78f + wobble;
    }

    /**
     * Scatter small debris specks around a loose-material print.
     */
    private static void emitGrains(VertexSink sink, float cx, float cy, float cz,
            float size, float cos, float sin, int count,
            int color, int alpha, int seed) {

        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;

        for (int i = 0; i < count; i++) {
            int gs = hash(seed + i * 7919);
            float angle = unit(gs) * (float) (Math.PI * 2.0);
            float dist = (0.95f + unit(hash(gs)) * 0.5f) * size;
            float grain = size * (0.05f + unit(hash(gs * 3 + 1)) * 0.06f);

            float localX = (float) Math.cos(angle) * dist;
            float localZ = (float) Math.sin(angle) * dist;

            float wx = localX * cos - localZ * sin;
            float wz = localX * sin + localZ * cos;

            sink.vertex(cx + wx - grain, cy, cz + wz - grain, r, g, b, alpha);
            sink.vertex(cx + wx - grain, cy, cz + wz + grain, r, g, b, alpha);
            sink.vertex(cx + wx + grain, cy, cz + wz + grain, r, g, b, alpha);
            sink.vertex(cx + wx + grain, cy, cz + wz - grain, r, g, b, alpha);
        }
    }

    /** Cheap deterministic integer scramble. */
    private static int hash(int value) {
        int h = value * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return h;
    }

    /** Map a hashed int to 0..1. */
    private static float unit(int hashed) {
        return (hashed >>> 8 & 0xFFFF) / 65535f;
    }
}
