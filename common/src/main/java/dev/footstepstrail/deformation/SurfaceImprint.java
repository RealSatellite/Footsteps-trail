package dev.footstepstrail.deformation;

/** Immutable pressure stamp, safe to share with chunk compilation workers. */
public final class SurfaceImprint {
    public final double x, y, z, size, strength;
    /** Fixed at contact from the surface material: rounds the outline and widens the rim. */
    public final double softness;
    private final double sin, cos, phase;
    private final boolean left;

    public SurfaceImprint(double x, double y, double z, double yaw, double size,
            double strength, int seed) {
        this(x, y, z, yaw, size, strength, seed, 0);
    }

    public SurfaceImprint(double x, double y, double z, double yaw, double size,
            double strength, int seed, double softness) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.size = Math.max(0.05, Math.min(2, size));
        this.strength = Math.max(0, Math.min(2, strength));
        this.softness = Math.max(0, Math.min(1, softness));
        this.sin = Math.sin(Math.toRadians(yaw));
        this.cos = Math.cos(Math.toRadians(yaw));
        this.left = (seed & 1) == 0;
        this.phase = (seed * 0.61803398875 % 1) * Math.PI * 2;
    }

    /** Covers the widest soft rim, so support blocks never clip it. */
    public double radius() { return size * (1.65 + 0.25 * softness); }

    private SurfaceImprint(SurfaceImprint source, double strength) {
        this.x = source.x; this.y = source.y; this.z = source.z;
        this.size = source.size; this.sin = source.sin; this.cos = source.cos;
        this.phase = source.phase; this.left = source.left;
        this.softness = source.softness;
        this.strength = Math.max(0, Math.min(2, strength));
    }

    /** Aging must never change the random outline, orientation, or handedness. */
    public SurfaceImprint withStrength(double strength) {
        return new SurfaceImprint(this, strength);
    }

    public boolean sameGeometry(SurfaceImprint other) {
        return x == other.x && y == other.y && z == other.z && size == other.size
                && sin == other.sin && cos == other.cos && phase == other.phase
                && left == other.left && strength == other.strength
                && softness == other.softness;
    }

    /** Signed displacement: negative sole, positive displaced material. Compact support. */
    public double height(double wx, double wz, DeformationMaterial material) {
        double soft = softness;
        double dx = (wx - x) / size;
        double dz = (wz - z) / size;
        double side = dx * cos + dz * sin;
        double forward = -dx * sin + dz * cos;
        if (!left) side = -side;
        if (Math.abs(side) > 1.3 + 0.35 * soft || Math.abs(forward) > 1.6 + 0.35 * soft) return 0;

        // Square Minecraft boot sole: a rounded box, slightly longer than wide so
        // walking direction stays readable. The small corner radius keeps mesh
        // shading smooth while the print reads as a square dent. Soft surfaces
        // round it into a squashed pad.
        double halfW = 0.62;
        double halfL = 0.70;
        double corner = 0.16 + 0.34 * soft;
        double qx = Math.abs(side) - (halfW - corner);
        double qz = Math.abs(forward) - (halfL - corner);
        double ax = Math.max(qx, 0);
        double az = Math.max(qz, 0);
        double sd = Math.sqrt(ax * ax + az * az) + Math.min(Math.max(qx, qz), 0) - corner;
        double r = sd + 1;
        // Straight-edge roughness along the sole axes instead of an angular wobble,
        // so edges and corners stay square on rough ground.
        double noise = Math.sin(side * 21 + phase) * 0.5 + Math.sin(forward * 17 - phase * 1.7) * 0.5;
        r += noise * material.roughness * 0.28 * smooth(0.55, 1, r);
        // Soft walls slope in gradually instead of dropping sharply.
        double inside = 1 - smooth(0.68 - 0.30 * soft, 1.02 + 0.06 * soft, r);
        double pressure = 0.60 + 0.40 * Math.min(1, Math.abs(forward) * 1.5);
        // A soft surface spreads the load, so the floor of the dent is even.
        pressure += (0.92 - pressure) * soft;
        double tread = 1;
        if (material != DeformationMaterial.GRAVEL && soft == 0 && material.softness == 0) {
            // Square lug tread: a shallow grid of grooves, softened to avoid moire/sharp spikes.
            double grooveF = Math.pow(0.5 + 0.5 * Math.cos(forward * 18 + phase), 8);
            double grooveS = Math.pow(0.5 + 0.5 * Math.cos(side * 18 - phase), 8);
            tread -= 0.07 * Math.max(grooveF, grooveS);
        }
        double rimPeak = 1.06 + 0.10 * soft;
        double ridge = smooth(0.93, rimPeak, r) * (1 - smooth(rimPeak, 1.36 + 0.30 * soft, r));
        double direction = 0.85 + 0.15 * forward + noise * material.roughness;
        return material.depth * strength * (-inside * pressure * tread
                + ridge * material.rim * direction);
    }

    /**
     * Pixel-art boot print for hard blocks, toe first, one character per texel at the default
     * size: toe, sole with an arch notch, a gap, then the heel. A filled box would read as one
     * more brick on brick-like textures; this outline never lines up with mortar lines.
     */
    private static final String[] PIXEL_SOLE = {
            ".##.",
            "####",
            "####",
            "###.",
            "....",
            ".##.",
            ".##."};
    /** Print size at which one sprite character is one texel. */
    private static final double PIXEL_SOLE_SIZE = 0.2;

    /** Half the pixel print's length, in blocks, so callers can bound the texels it can touch. */
    public double pixelReach() {
        return PIXEL_SOLE.length / 2.0 / 16 * (size / PIXEL_SOLE_SIZE) * Math.sqrt(2);
    }

    /** Whether this stamp's pixel boot print covers the point, rotated with the step. */
    public boolean covers(double wx, double wz) {
        double texels = 16 * PIXEL_SOLE_SIZE / size;
        double dx = (wx - x) * texels;
        double dz = (wz - z) * texels;
        double side = dx * cos + dz * sin;
        double forward = -dx * sin + dz * cos;
        // Right feet are the mirror image of left feet.
        if (!left) side = -side;
        int row = (int) Math.floor(PIXEL_SOLE.length / 2.0 - forward);
        int col = (int) Math.floor(side + PIXEL_SOLE[0].length() / 2.0);
        return row >= 0 && row < PIXEL_SOLE.length && col >= 0 && col < PIXEL_SOLE[row].length()
                && PIXEL_SOLE[row].charAt(col) == '#';
    }

    public static double smooth(double a, double b, double value) {
        double t = Math.max(0, Math.min(1, (value - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
