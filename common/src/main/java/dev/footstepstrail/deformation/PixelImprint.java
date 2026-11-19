package dev.footstepstrail.deformation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Minecraft-style terrain prints: a pixel-art boot print (see {@link SurfaceImprint#covers}) cut
 * into the block on its own 1/16 texel grid, with small vertical walls.
 *
 * <p>A smooth dent cannot move the texture visibly, so all that shows is a blurred darkness
 * gradient, and on dark textures black patches. A print cut on the texel grid reads on any
 * texture. Each texel is cut ({@link DeformationMaterial#pixelDepth}), untouched, or, on loose
 * ground, raised a little ({@link DeformationMaterial#pixelRim}) where it borders the print, so
 * the print gets a thin pushed-up edge that follows its outline. Heights are in texels and may be
 * fractions of one. Floors and walls are shaded like a small pit, darker on the walls facing east
 * and west, as Minecraft shades block sides. The outline never changes; a fading print only gets
 * shallower.
 */
final class PixelImprint {
    static final double PIXEL = 1 / 16.0;
    /**
     * Floor of a cut one texel deep: darker than vanilla ambient occlusion so the print still
     * separates from busy textures such as bricks, without going near black. Shallower cuts are
     * shaded proportionally less.
     */
    static final double FLOOR_SHADE = 0.75;
    /** Walls facing east or west, a step darker than vanilla's side-face shading. */
    static final double WALL_SHADE_X = 0.55;
    /** Walls facing north or south, a step darker than vanilla's side-face shading. */
    static final double WALL_SHADE_Z = 0.7;
    /** Stamp strength at and above which a print has its full depth; weaker prints are shallower. */
    static final double FULL_STRENGTH = 0.5;
    /** Below this stamp strength a print is gone. */
    static final double GONE_BELOW = 0.03;

    /**
     * Texel height at a world point on a surface at height {@code y}, in texels: negative is cut,
     * 0 untouched, positive raised. Null where the block there does not draw pixel prints.
     */
    @FunctionalInterface
    interface Levels { Double level(double x, double y, double z); }

    private PixelImprint() {}

    /**
     * Height, in texels, of the texel containing (x, z), for stamps on a surface of this material
     * at height {@code y}, {@code thickness} above the bottom of its block.
     */
    static double level(List<SurfaceImprint> stamps, DeformationMaterial material, double thickness,
            double x, double y, double z) {
        // Never cut through a thin layer: at least half of it stays. A one-texel carpet still
        // takes a half-texel print; requiring a whole texel to remain left carpets unmarked.
        double depth = Math.min(material.pixelDepth, thickness * 16 * 0.5);
        if (depth <= 0) return 0;
        double cx = (Math.floor(x * 16) + 0.5) * PIXEL, cz = (Math.floor(z * 16) + 0.5) * PIXEL;
        double cut = 0, rim = 0;
        for (SurfaceImprint stamp : stamps) {
            double above = stamp.y - y;
            if (above < -0.015 || above > 0.05 || stamp.strength < GONE_BELOW) continue;
            double fade = Math.min(1, stamp.strength / FULL_STRENGTH);
            if (stamp.covers(cx, cz)) cut = Math.max(cut, depth * fade);
            else if (material.pixelRim > 0 && borders(stamp, cx, cz)) rim = Math.max(rim, material.pixelRim * fade);
        }
        return cut > 0 ? -cut : rim;
    }

    /** Whether the print covers a texel sharing an edge with this texel centre. */
    private static boolean borders(SurfaceImprint stamp, double cx, double cz) {
        return stamp.covers(cx + PIXEL, cz) || stamp.covers(cx - PIXEL, cz)
                || stamp.covers(cx, cz + PIXEL) || stamp.covers(cx, cz - PIXEL);
    }

    static void emit(TerrainQuad quad, int bx, int by, int bz, List<SurfaceImprint> stamps, Levels levels,
            Consumer<TerrainQuad> output) {
        Frame frame = Frame.of(quad, bx, by, bz);
        // Only flat, axis-aligned tops are cut; side faces keep their original quad.
        if (frame == null) {
            output.accept(quad);
            return;
        }
        double minA = Double.POSITIVE_INFINITY, maxA = Double.NEGATIVE_INFINITY;
        double minB = minA, maxB = maxA;
        for (SurfaceImprint stamp : stamps) {
            double above = stamp.y - frame.y;
            if (stamp.strength < GONE_BELOW || above < -0.015 || above > 0.05) continue;
            double reach = stamp.pixelReach() + 2 * PIXEL;
            double a = frame.uAlongX ? stamp.x : stamp.z, b = frame.uAlongX ? stamp.z : stamp.x;
            minA = Math.min(minA, a - reach); maxA = Math.max(maxA, a + reach);
            minB = Math.min(minB, b - reach); maxB = Math.max(maxB, b + reach);
        }
        if (minA > maxA) {
            output.accept(quad);
            return;
        }
        float[] us = lines(frame.a0, frame.aLen, minA, maxA);
        float[] vs = lines(frame.b0, frame.bLen, minB, maxB);
        float du = (float) Math.min(1, PIXEL / Math.abs(frame.aLen));
        float dv = (float) Math.min(1, PIXEL / Math.abs(frame.bLen));
        double[][] grid = new double[us.length - 1][vs.length - 1];
        boolean any = false;
        for (int i = 0; i + 1 < us.length; i++) {
            for (int j = 0; j + 1 < vs.length; j++) {
                float cu = (us[i] + us[i + 1]) / 2, cv = (vs[j] + vs[j + 1]) / 2;
                Double level = levels.level(frame.x(cu, cv), frame.y, frame.z(cu, cv));
                grid[i][j] = level == null ? 0 : level;
                any |= grid[i][j] != 0;
            }
        }
        if (!any) {
            output.accept(quad);
            return;
        }
        double wallA = frame.uAlongX ? WALL_SHADE_X : WALL_SHADE_Z;
        double wallB = frame.uAlongX ? WALL_SHADE_Z : WALL_SHADE_X;
        float hu = du / 2, hv = dv / 2;
        for (int i = 0; i + 1 < us.length; i++) {
            for (int j = 0; j + 1 < vs.length; j++) {
                float u0 = us[i], u1 = us[i + 1], v0 = vs[j], v1 = vs[j + 1];
                float cu = (u0 + u1) / 2, cv = (v0 + v1) / 2;
                double level = grid[i][j];
                double shade = level >= 0 ? 1 : 1 - (1 - FLOOR_SHADE) * Math.min(1, -level);
                output.accept(cell(quad, u0, v0, u1, v1, height(quad, level), shade));
                Double n = i > 0 ? Double.valueOf(grid[i - 1][j])
                        : levels.level(frame.x(u0 - hu, cv), frame.y, frame.z(u0 - hu, cv));
                side(output, frame, quad, level, n, u0, v0, u0, v1, du, 0, cu, cv, u0 - hu, cv, wallA);
                n = i + 2 < us.length ? Double.valueOf(grid[i + 1][j])
                        : levels.level(frame.x(u1 + hu, cv), frame.y, frame.z(u1 + hu, cv));
                side(output, frame, quad, level, n, u1, v0, u1, v1, -du, 0, cu, cv, u1 + hu, cv, wallA);
                n = j > 0 ? Double.valueOf(grid[i][j - 1])
                        : levels.level(frame.x(cu, v0 - hv), frame.y, frame.z(cu, v0 - hv));
                side(output, frame, quad, level, n, u0, v0, u1, v0, 0, dv, cu, cv, cu, v0 - hv, wallB);
                n = j + 2 < vs.length ? Double.valueOf(grid[i][j + 1])
                        : levels.level(frame.x(cu, v1 + hv), frame.y, frame.z(cu, v1 + hv));
                side(output, frame, quad, level, n, u0, v1, u1, v1, 0, -dv, cu, cv, cu, v1 + hv, wallB);
            }
        }
    }

    private static float height(TerrainQuad quad, double level) {
        return (float) (quad.y[0] + level * PIXEL);
    }

    /**
     * The wall on one edge (ea..eb) of a texel, if this texel draws it. Between two pixel texels
     * the lower one draws the wall, facing itself. A block that draws no pixel prints (level
     * null) is flat at the original top and draws nothing, so a raised texel next to it draws its
     * own outer wall. (tu, tv) is the quad-space step from the edge into this texel, used for the
     * wall's texture; (cu, cv) is this texel's centre, (nu, nv) a point just across the edge.
     */
    private static void side(Consumer<TerrainQuad> output, Frame frame, TerrainQuad quad, double level,
            Double neighbour, float eau, float eav, float ebu, float ebv, float tu, float tv,
            float cu, float cv, float nu, float nv, double shade) {
        double top, bottom;
        float faceU = cu, faceV = cv;
        double other = neighbour == null ? 0 : neighbour;
        if (other > level) {
            top = other;
            bottom = level;
        } else if (neighbour == null && level > 0) {
            top = level;
            bottom = 0;
            faceU = nu;
            faceV = nv;
        } else {
            return;
        }
        // The wall shows the texels just inside the edge, as far in as the wall is tall.
        float span = (float) (top - bottom);
        float iau = clamp(eau + tu * span), iav = clamp(eav + tv * span);
        float ibu = clamp(ebu + tu * span), ibv = clamp(ebv + tv * span);
        output.accept(frame.face(wall(quad, eau, eav, ebu, ebv, iau, iav, ibu, ibv,
                height(quad, top), height(quad, bottom), shade), faceU, faceV));
    }

    private static float clamp(float t) {
        return Math.max(0, Math.min(1, t));
    }

    /** Quad-space cut lines: the quad edges plus every texel boundary inside [lo, hi]. */
    private static float[] lines(double start, double length, double lo, double hi) {
        List<Float> cuts = new ArrayList<>();
        cuts.add(0f);
        cuts.add(1f);
        for (long k = (long) Math.ceil(lo * 16); k <= (long) Math.floor(hi * 16); k++) {
            float t = (float) ((k * PIXEL - start) / length);
            if (t > 0.0001f && t < 0.9999f) cuts.add(t);
        }
        float[] result = new float[cuts.size()];
        for (int i = 0; i < result.length; i++) result[i] = cuts.get(i);
        Arrays.sort(result);
        return result;
    }

    /** A horizontal piece of the source quad at height {@code y}, in the source winding order. */
    private static TerrainQuad cell(TerrainQuad source, float u0, float v0, float u1, float v1, float y, double shade) {
        TerrainQuad quad = new TerrainQuad();
        float[] us = {u0, u1, u1, u0}, vs = {v0, v0, v1, v1};
        for (int i = 0; i < 4; i++) {
            sample(source, quad, i, us[i], vs[i], us[i], vs[i], shade);
            quad.y[i] = y;
        }
        return quad;
    }

    /**
     * A vertical wall along the edge (ea..eb) from {@code top} down to {@code floor}. Its texture is
     * the texels just inside the edge (ia..ib), the way Minecraft textures a one-pixel step.
     */
    private static TerrainQuad wall(TerrainQuad source, float eau, float eav, float ebu, float ebv,
            float iau, float iav, float ibu, float ibv, float top, float floor, double shade) {
        TerrainQuad quad = new TerrainQuad();
        sample(source, quad, 0, eau, eav, eau, eav, shade);
        sample(source, quad, 1, ebu, ebv, ebu, ebv, shade);
        sample(source, quad, 2, ebu, ebv, ibu, ibv, shade);
        sample(source, quad, 3, eau, eav, iau, iav, shade);
        quad.y[0] = quad.y[1] = top;
        quad.y[2] = quad.y[3] = floor;
        return quad;
    }

    /** Position at (pu, pv), texture at (tu, tv); colour and light from the position. */
    private static void sample(TerrainQuad source, TerrainQuad quad, int i, float pu, float pv,
            float tu, float tv, double shade) {
        quad.x[i] = QuadDeformation.blend(source.x, pu, pv);
        quad.z[i] = QuadDeformation.blend(source.z, pu, pv);
        quad.u[i] = QuadDeformation.blend(source.u, tu, tv);
        quad.v[i] = QuadDeformation.blend(source.v, tu, tv);
        int color = QuadDeformation.interpolate(source.color, pu, pv, 8, 255);
        int r = (int) Math.min(255, ((color >>> 16) & 255) * shade);
        int g = (int) Math.min(255, ((color >>> 8) & 255) * shade);
        int b = (int) Math.min(255, (color & 255) * shade);
        quad.color[i] = (color & 0xff000000) | (r << 16) | (g << 8) | b;
        quad.light[i] = QuadDeformation.interpolate(source.light, pu, pv, 16, 65535);
    }

    /** World mapping of a flat, axis-aligned, rectangular source quad. */
    private static final class Frame {
        final boolean uAlongX;
        /** World coordinate along the u axis is a0 + u * aLen; along the v axis b0 + v * bLen. */
        final double a0, aLen, b0, bLen, y;
        /** +1 when the source winding faces up with the standard cross product, else -1. */
        final double winding;
        private final int blockX, blockZ;

        private Frame(boolean uAlongX, double a0, double aLen, double b0, double bLen, double y, double winding,
                int blockX, int blockZ) {
            this.uAlongX = uAlongX; this.a0 = a0; this.aLen = aLen; this.b0 = b0; this.bLen = bLen;
            this.y = y; this.winding = winding; this.blockX = blockX; this.blockZ = blockZ;
        }

        static Frame of(TerrainQuad q, int bx, int by, int bz) {
            for (int i = 1; i < 4; i++) if (Math.abs(q.y[i] - q.y[0]) > 1.0e-5) return null;
            double ux = q.x[1] - q.x[0], uz = q.z[1] - q.z[0];
            double vx = q.x[3] - q.x[0], vz = q.z[3] - q.z[0];
            boolean uAlongX = Math.abs(ux) > Math.abs(uz);
            double aLen = uAlongX ? ux : uz, aSkew = uAlongX ? uz : ux;
            double bLen = uAlongX ? vz : vx, bSkew = uAlongX ? vx : vz;
            if (Math.abs(aLen) < 1.0e-4 || Math.abs(bLen) < 1.0e-4
                    || Math.abs(aSkew) > 1.0e-5 || Math.abs(bSkew) > 1.0e-5) return null;
            // Must be a true rectangle: the far corner closes the parallelogram.
            if (Math.abs(q.x[2] - (q.x[1] + vx)) > 1.0e-4 || Math.abs(q.z[2] - (q.z[1] + vz)) > 1.0e-4) return null;
            // Y of cross(edge u, edge v); Minecraft's up faces wind so their front faces up.
            double normalY = uz * vx - ux * vz;
            double a0 = uAlongX ? bx + q.x[0] : bz + q.z[0];
            double b0 = uAlongX ? bz + q.z[0] : bx + q.x[0];
            return new Frame(uAlongX, a0, aLen, b0, bLen, by + q.y[0], normalY >= 0 ? 1 : -1, bx, bz);
        }

        double x(double u, double v) { return uAlongX ? a0 + u * aLen : b0 + v * bLen; }
        double z(double u, double v) { return uAlongX ? b0 + v * bLen : a0 + u * aLen; }

        /** Orient a wall so its front faces the point (cu, cv). */
        TerrainQuad face(TerrainQuad wall, double cu, double cv) {
            double ax = wall.x[1] - wall.x[0], ay = wall.y[1] - wall.y[0], az = wall.z[1] - wall.z[0];
            double bx = wall.x[3] - wall.x[0], by = wall.y[3] - wall.y[0], bz = wall.z[3] - wall.z[0];
            // cross(edge 1, edge 3) with the source quad's sign is the wall's front normal.
            double nx = (ay * bz - az * by) * winding;
            double nz = (ax * by - ay * bx) * winding;
            double tx = x(cu, cv) - blockX - (wall.x[0] + wall.x[1]) / 2;
            double tz = z(cu, cv) - blockZ - (wall.z[0] + wall.z[1]) / 2;
            return nx * tx + nz * tz > 0 ? wall : reverse(wall);
        }
    }

    private static TerrainQuad reverse(TerrainQuad q) {
        TerrainQuad r = new TerrainQuad();
        for (int i = 0; i < 4; i++) {
            int s = 3 - i;
            r.x[i] = q.x[s]; r.y[i] = q.y[s]; r.z[i] = q.z[s];
            r.u[i] = q.u[s]; r.v[i] = q.v[s];
            r.color[i] = q.color[s]; r.light[i] = q.light[s];
        }
        return r;
    }
}
