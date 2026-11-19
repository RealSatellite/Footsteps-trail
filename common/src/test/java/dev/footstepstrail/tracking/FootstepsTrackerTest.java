package dev.footstepstrail.tracking;

import com.google.gson.Gson;
import dev.footstepstrail.config.FootstepsConfig;
import dev.footstepstrail.config.SurfaceProfile;
import dev.footstepstrail.config.TrackingMode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.junit.Assert.*;

public class FootstepsTrackerTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    private FootstepsConfig config;
    private FootstepsTracker tracker;

    @Before public void setUp() {
        config = new Gson().fromJson("{}", FootstepsConfig.class);
        config.setEnabled(true);
        config.setMinDistanceForStep(1.0);
        tracker = FootstepsTracker.getInstance();
        tracker.clear();
    }

    @After public void tearDown() {
        tracker.clear();
    }

    private void step(double x, String dimension, int blockX) {
        tracker.recordCandidate(x, 65, 0.5, 0, dimension, false, config,
                SurfaceProfile.HARD, Footstep.DEFAULT_COLOR, blockX, 64, 0);
    }

    @Test public void combinedModeIsTheDefault() {
        assertEquals(TrackingMode.COMBINED, config.getTrackingMode());
    }

    @Test public void combinedDropsStepsPastTheStepLimit() {
        config.setTrackingMode(TrackingMode.COMBINED);
        config.setMaxSteps(3);
        for (int i = 0; i < 5; i++) step(0.5 + 2 * i, OVERWORLD, 2 * i);
        tracker.removeExpiredFootsteps(config);
        assertEquals(3, tracker.getCount());
    }

    @Test public void combinedDropsOldStepsEvenUnderTheStepLimit() throws InterruptedException {
        config.setTrackingMode(TrackingMode.COMBINED);
        config.setMaxSteps(50);
        config.setMaxDurationSeconds(1);
        step(0.5, OVERWORLD, 0);
        Thread.sleep(1150);
        step(3.5, OVERWORLD, 3);
        tracker.removeExpiredFootsteps(config);
        assertEquals(1, tracker.getCount());
    }

    @Test public void timeModeKeepsStepsBeyondTheStepLimit() {
        config.setTrackingMode(TrackingMode.TIME);
        config.setMaxSteps(2);
        for (int i = 0; i < 5; i++) step(0.5 + 2 * i, OVERWORLD, 2 * i);
        tracker.removeExpiredFootsteps(config);
        assertEquals(5, tracker.getCount());
    }

    @Test public void combinedProgressFollowsWhicheverLimitIsNearer() {
        assertEquals(0.8f, TrackingMode.COMBINED.progress(0.2f, 0.8f), 1e-6);
        assertEquals(0.9f, TrackingMode.COMBINED.progress(0.9f, 0.1f), 1e-6);
        assertEquals(0.2f, TrackingMode.TIME.progress(0.2f, 0.8f), 1e-6);
        assertEquals(0.8f, TrackingMode.STEPS.progress(0.2f, 0.8f), 1e-6);
        assertEquals(1f, TrackingMode.COMBINED.progress(3f, 0f), 0);
    }

    @Test public void stepsOnBrokenBlocksAreRemoved() {
        step(0.5, OVERWORLD, 0);
        step(3.5, OVERWORLD, 3);
        step(6.5, OVERWORLD, 6);
        assertEquals(3, tracker.getCount());

        tracker.removeUnsupported(OVERWORLD, (x, y, z) -> x != 3);

        assertEquals(2, tracker.getCount());
        Set<Integer> remaining = new HashSet<>();
        for (Iterator<Footstep> it = tracker.getFootsteps(); it.hasNext(); ) {
            remaining.add(it.next().getSupportX());
        }
        assertFalse(remaining.contains(3));
    }

    @Test public void otherDimensionsAreNotChecked() {
        step(0.5, NETHER, 0);
        tracker.removeUnsupported(OVERWORLD, (x, y, z) -> false);
        assertEquals(1, tracker.getCount());
    }

    @Test public void stepsWithoutKnownSupportAreKept() {
        tracker.recordCandidate(0.5, 65, 0.5, 0, OVERWORLD, false, config,
                SurfaceProfile.HARD, Footstep.DEFAULT_COLOR);
        tracker.removeUnsupported(OVERWORLD, (x, y, z) -> false);
        assertEquals(1, tracker.getCount());
    }

    @Test public void supportBlockIsRecorded() {
        step(0.5, OVERWORLD, 7);
        Footstep footstep = tracker.getFootsteps().next();
        assertTrue(footstep.hasSupport());
        assertEquals(7, footstep.getSupportX());
        assertEquals(64, footstep.getSupportY());
        assertEquals(0, footstep.getSupportZ());
    }
}
