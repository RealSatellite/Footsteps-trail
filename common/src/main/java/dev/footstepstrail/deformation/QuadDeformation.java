package dev.footstepstrail.deformation;

import java.util.List;
import java.util.function.Consumer;

/** The same subdivision, displacement, texture interpolation and shading on every Minecraft version. */
public final class QuadDeformation {
    private final TerrainQuad original;
    private final int blockX, blockY, blockZ;
    private final DeformationMaterial material;
    private final List<SurfaceImprint> stamps;
    private final SurfaceHeights.Sampler heights;
    private final Consumer<TerrainQuad> output;

    private QuadDeformation(TerrainQuad original, int x, int y, int z, DeformationMaterial material,
            List<SurfaceImprint> stamps, SurfaceHeights.Sampler heights, Consumer<TerrainQuad> output) {
        this.original = original;
        this.blockX = x; this.blockY = y; this.blockZ = z;
        this.material = material; this.stamps = stamps;
        this.heights = heights; this.output = output;
    }

    static void emit(TerrainQuad original, int x, int y, int z, DeformationMaterial material,
            List<SurfaceImprint> stamps, SurfaceHeights.Sampler heights, Consumer<TerrainQuad> output) {
        new QuadDeformation(original, x, y, z, material, stamps, heights, output).split(0, 0, 1, 1, 0);
    }

    private void split(float u0, float v0, float u1, float v1, int depth) {
        float[] us = {u0, u1, u1, u0}, vs = {v0, v0, v1, v1};
        TerrainQuad quad = new TerrainQuad();
        for (int i = 0; i < 4; i++) {
            quad.x[i] = blend(original.x, us[i], vs[i]);
            quad.y[i] = blend(original.y, us[i], vs[i]);
            quad.z[i] = blend(original.z, us[i], vs[i]);
        }
        if (depth < 5 && intersects(quad)) {
            float u = (u0 + u1) * 0.5f, v = (v0 + v1) * 0.5f;
            split(u0, v0, u, v, depth + 1); split(u, v0, u1, v, depth + 1);
            split(u, v, u1, v1, depth + 1); split(u0, v, u, v1, depth + 1);
            return;
        }
        for (int i = 0; i < 4; i++) {
            double wx = blockX + quad.x[i], wy = blockY + quad.y[i], wz = blockZ + quad.z[i];
            double h = heights.height(wx, wy, wz);
            double sx = (heights.height(wx + 0.008, wy, wz) - heights.height(wx - 0.008, wy, wz)) / 0.016;
            double sz = (heights.height(wx, wy, wz + 0.008) - heights.height(wx, wy, wz - 0.008)) / 0.016;
            double cavity = Math.min(1, Math.max(0, -h) / material.depth);
            double shade = Math.max(0.5, Math.min(1.08, (1 - 0.22 * sx + 0.16 * sz)
                    / Math.sqrt(1 + sx * sx + sz * sz) - cavity * material.cavityContrast()));
            quad.y[i] += (float) h;
            int color = interpolate(original.color, us[i], vs[i], 8, 255);
            int r = (int) Math.min(255, ((color >>> 16) & 255) * shade);
            int g = (int) Math.min(255, ((color >>> 8) & 255) * shade);
            int b = (int) Math.min(255, (color & 255) * shade);
            quad.color[i] = (color & 0xff000000) | (r << 16) | (g << 8) | b;
            quad.light[i] = interpolate(original.light, us[i], vs[i], 16, 65535);
            quad.u[i] = blend(original.u, us[i], vs[i]);
            quad.v[i] = blend(original.v, us[i], vs[i]);
        }
        output.accept(quad);
    }

    private boolean intersects(TerrainQuad quad) {
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (int i = 0; i < 4; i++) {
            minX = Math.min(minX, blockX + quad.x[i]); maxX = Math.max(maxX, blockX + quad.x[i]);
            minY = Math.min(minY, blockY + quad.y[i]); maxY = Math.max(maxY, blockY + quad.y[i]);
            minZ = Math.min(minZ, blockZ + quad.z[i]); maxZ = Math.max(maxZ, blockZ + quad.z[i]);
        }
        for (SurfaceImprint stamp : stamps) {
            double r = stamp.radius();
            if (maxY >= stamp.y - 0.26 && minY <= stamp.y + 0.015 && maxX >= stamp.x - r
                    && minX <= stamp.x + r && maxZ >= stamp.z - r && minZ <= stamp.z + r) return true;
        }
        return false;
    }

    static float blend(float[] values, float u, float v) {
        return blend(values[0], values[1], values[2], values[3], u, v);
    }

    private static float blend(float a, float b, float c, float d, float u, float v) {
        return a * (1 - u) * (1 - v) + b * u * (1 - v) + c * u * v + d * (1 - u) * v;
    }

    static int interpolate(int[] values, float u, float v, int bits, int mask) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += bits) {
            result |= Math.round(blend((values[0] >>> shift) & mask, (values[1] >>> shift) & mask,
                    (values[2] >>> shift) & mask, (values[3] >>> shift) & mask, u, v)) << shift;
        }
        return result;
    }
}
