package dev.footstepstrail.tracking;

import dev.footstepstrail.config.SurfaceProfile;

/**
 * Data class representing a single footstep marker.
 */
public class Footstep {
    /** Neutral grey, used when no surface was sampled. */
    public static final int DEFAULT_COLOR = 0xB0B0B0;

    private final double x;
    private final double y;
    private final double z;
    private final long timestamp;
    private final int stepIndex;
    private final float yaw;
    private final String dimension;
    private final SurfaceProfile surfaceProfile;
    private final int baseColor;
    private final boolean hasSupport;
    private final int supportX;
    private final int supportY;
    private final int supportZ;

    public Footstep(double x, double y, double z, long timestamp, int stepIndex, float yaw, String dimension) {
        this(x, y, z, timestamp, stepIndex, yaw, dimension, SurfaceProfile.HARD, DEFAULT_COLOR);
    }

    public Footstep(double x, double y, double z, long timestamp, int stepIndex, float yaw, String dimension,
            SurfaceProfile surfaceProfile, int baseColor) {
        this(x, y, z, timestamp, stepIndex, yaw, dimension, surfaceProfile, baseColor, false, 0, 0, 0);
    }

    /**
     * A footstep resting on the block at {@code supportX, supportY, supportZ}, so it can be
     * removed once that block is broken.
     */
    public Footstep(double x, double y, double z, long timestamp, int stepIndex, float yaw, String dimension,
            SurfaceProfile surfaceProfile, int baseColor, int supportX, int supportY, int supportZ) {
        this(x, y, z, timestamp, stepIndex, yaw, dimension, surfaceProfile, baseColor, true, supportX, supportY, supportZ);
    }

    private Footstep(double x, double y, double z, long timestamp, int stepIndex, float yaw, String dimension,
            SurfaceProfile surfaceProfile, int baseColor, boolean hasSupport, int supportX, int supportY, int supportZ) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.timestamp = timestamp;
        this.stepIndex = stepIndex;
        this.yaw = yaw;
        this.dimension = dimension;
        this.surfaceProfile = surfaceProfile != null ? surfaceProfile : SurfaceProfile.HARD;
        this.baseColor = baseColor;
        this.hasSupport = hasSupport;
        this.supportX = supportX;
        this.supportY = supportY;
        this.supportZ = supportZ;
    }

    /** True when the block this step rests on is known. */
    public boolean hasSupport() {
        return hasSupport;
    }

    public int getSupportX() {
        return supportX;
    }

    public int getSupportY() {
        return supportY;
    }

    public int getSupportZ() {
        return supportZ;
    }

    /**
     * The kind of surface this step landed on, sampled once when recorded.
     */
    public SurfaceProfile getSurfaceProfile() {
        return surfaceProfile;
    }

    /**
     * The colour of the block that was stepped on, packed as 0xRRGGBB.
     *
     * <p>
     * Captured at record time so the mark survives the block being broken or
     * its chunk unloading.
     * </p>
     */
    public int getBaseColor() {
        return baseColor;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public float getYaw() {
        return yaw;
    }

    public String getDimension() {
        return dimension;
    }

    /**
     * Calculate the squared distance to another position.
     */
    public double distanceSquaredTo(double otherX, double otherY, double otherZ) {
        double dx = this.x - otherX;
        double dy = this.y - otherY;
        double dz = this.z - otherZ;
        return dx * dx + dy * dy + dz * dz;
    }
}
