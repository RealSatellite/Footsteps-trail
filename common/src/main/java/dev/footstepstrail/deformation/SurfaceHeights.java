package dev.footstepstrail.deformation;

import java.util.List;

/** Shared height field in world coordinates, including saturation and thin-surface limits. */
final class SurfaceHeights {
    @FunctionalInterface
    interface Sampler { double height(double x, double y, double z); }

    static double sample(double x, double y, double z, int blockY,
            DeformationMaterial material, List<SurfaceImprint> stamps) {
        double down = 0, up = 0;
        for (SurfaceImprint stamp : stamps) {
            double below = stamp.y - y;
            if (below < -0.015 || below > 0.26) continue;
            double h = stamp.height(x, z, material) * (1 - SurfaceImprint.smooth(0.08, 0.26, below));
            if (h < 0) down -= h; else up += h;
        }
        double limit = Math.min(material.depth * 1.65, Math.max(0, y - blockY) * 0.65);
        return (limit == 0 ? 0 : -limit * (1 - Math.exp(-down / limit)))
                + material.depth * 0.65 * (1 - Math.exp(-up / (material.depth * 0.65)));
    }

    /** Partition of unity around a block edge; both blocks sample exactly the same blend. */
    static double weight(double coordinate, int block) {
        return SurfaceImprint.smooth(block - 0.0625, block + 0.0625, coordinate)
                * (1 - SurfaceImprint.smooth(block + 0.9375, block + 1.0625, coordinate));
    }

    private SurfaceHeights() {}
}
