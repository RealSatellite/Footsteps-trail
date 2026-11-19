package dev.footstepstrail.tracking;

import dev.footstepstrail.config.FootstepsConfig;
import dev.footstepstrail.config.SurfaceProfile;
import dev.footstepstrail.config.TrackingMode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Manages the tracking and decay of footsteps.
 */
public class FootstepsTracker {
    /** Answers whether the block at a position can still hold a footstep. */
    public interface SupportCheck {
        boolean supports(int x, int y, int z);
    }

    private static FootstepsTracker INSTANCE;

    private final Deque<Footstep> footsteps = new ArrayDeque<>();
    private int stepCounter = 0;
    private double lastX = Double.NaN;
    private double lastY = Double.NaN;
    private double lastZ = Double.NaN;
    private String lastDimension = null;

    private FootstepsTracker() {
    }

    public static FootstepsTracker getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new FootstepsTracker();
        }
        return INSTANCE;
    }

    /**
     * Record a candidate footstep position computed by the platform implementation.
     *
     * <p>
     * Retained for loaders that do not sample the walked-on block; the step is
     * recorded with neutral surface data.
     * </p>
     */
    public void recordCandidate(double x, double y, double z, float yaw, String dimension, boolean isSprinting, FootstepsConfig config) {
        recordCandidate(x, y, z, yaw, dimension, isSprinting, config, SurfaceProfile.HARD, Footstep.DEFAULT_COLOR);
    }

    /**
     * Record a candidate footstep along with the surface it landed on.
     */
    public void recordCandidate(double x, double y, double z, float yaw, String dimension, boolean isSprinting,
            FootstepsConfig config, SurfaceProfile surfaceProfile, int baseColor) {
        if (config == null || !config.isEnabled()) {
            return;
        }

        if (shouldRecordFootstep(x, y, z, dimension, isSprinting, config)) {
            recordFootstep(x, y, z, yaw, dimension, surfaceProfile, baseColor);
        }
    }

    /**
     * Record a candidate footstep resting on the block at {@code blockX, blockY, blockZ}.
     * {@link #removeUnsupported} drops it once that block is gone.
     */
    public void recordCandidate(double x, double y, double z, float yaw, String dimension, boolean isSprinting,
            FootstepsConfig config, SurfaceProfile surfaceProfile, int baseColor, int blockX, int blockY, int blockZ) {
        if (config == null || !config.isEnabled()) {
            return;
        }

        if (shouldRecordFootstep(x, y, z, dimension, isSprinting, config)) {
            add(new Footstep(x, y, z, System.currentTimeMillis(), stepCounter++, yaw, dimension,
                    surfaceProfile, baseColor, blockX, blockY, blockZ));
        }
    }

    /**
     * Remove footsteps in {@code dimension} whose supporting block no longer holds them,
     * for example because it was broken. Steps without a known support block are kept.
     */
    public void removeUnsupported(String dimension, SupportCheck check) {
        if (check == null || footsteps.isEmpty()) {
            return;
        }
        Iterator<Footstep> iterator = footsteps.iterator();
        while (iterator.hasNext()) {
            Footstep footstep = iterator.next();
            if (!footstep.hasSupport()) {
                continue;
            }
            if (dimension != null && !dimension.equals(footstep.getDimension())) {
                continue;
            }
            if (!check.supports(footstep.getSupportX(), footstep.getSupportY(), footstep.getSupportZ())) {
                iterator.remove();
            }
        }
    }

    private boolean shouldRecordFootstep(double x, double y, double z, String dimension, boolean isSprinting, FootstepsConfig config) {
        // First footstep or dimension change - always record
        if (Double.isNaN(lastX) || lastDimension == null || !lastDimension.equals(dimension)) {
            return true;
        }

        double minDist = config.getMinDistanceForStep();
        if (isSprinting) {
            minDist *= 1.5;
        }
        double minDistSquared = minDist * minDist;

        double dx = x - lastX;
        double dy = y - lastY;
        double dz = z - lastZ;
        double distSquared = dx * dx + dy * dy + dz * dz;

        return distSquared >= minDistSquared;
    }

    private void recordFootstep(double x, double y, double z, float yaw, String dimension,
            SurfaceProfile surfaceProfile, int baseColor) {
        recordContact(x, y, z, yaw, dimension, surfaceProfile, baseColor);
    }

    /** Record an already resolved foot contact (including its left/right offset). */
    public Footstep recordContact(double x, double y, double z, float yaw, String dimension,
            SurfaceProfile surfaceProfile, int baseColor) {
        long now = System.currentTimeMillis();
        return add(new Footstep(x, y, z, now, stepCounter++, yaw, dimension, surfaceProfile, baseColor));
    }

    private Footstep add(Footstep footstep) {
        footsteps.addFirst(footstep);

        lastX = footstep.getX();
        lastY = footstep.getY();
        lastZ = footstep.getZ();
        lastDimension = footstep.getDimension();
        return footstep;
    }

    public void removeExpiredFootsteps(FootstepsConfig config) {
        if (config == null) {
            return;
        }
        TrackingMode mode = config.getTrackingMode();
        if (mode.usesTime()) {
            removeByTime(config.getMaxDurationSeconds());
        }
        // Time-only mode still caps the list so it cannot grow without bound.
        removeBySteps(mode.usesSteps() ? config.getMaxSteps() : 500);
    }

    private void removeByTime(int maxDurationSeconds) {
        long now = System.currentTimeMillis();
        long maxAgeMs = maxDurationSeconds * 1000L;

        // Remove from the end (oldest footsteps)
        while (!footsteps.isEmpty()) {
            Footstep oldest = footsteps.peekLast();
            if (oldest != null && (now - oldest.getTimestamp()) > maxAgeMs) {
                footsteps.removeLast();
            } else {
                break;
            }
        }
    }

    private void removeBySteps(int maxSteps) {
        while (footsteps.size() > maxSteps) {
            footsteps.removeLast();
        }
    }

    /**
     * Get an iterator over the footsteps (newest first).
     */
    public Iterator<Footstep> getFootsteps() {
        return footsteps.iterator();
    }

    /**
     * Get the total number of active footsteps.
     */
    public int getCount() {
        return footsteps.size();
    }

    /**
     * Clear all footsteps and reset tracking.
     */
    public void clear() {
        footsteps.clear();
        lastX = Double.NaN;
        lastY = Double.NaN;
        lastZ = Double.NaN;
        lastDimension = null;
    }
}
