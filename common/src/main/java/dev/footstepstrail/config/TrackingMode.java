package dev.footstepstrail.config;

/**
 * Enum defining the tracking mode for footsteps.
 */
public enum TrackingMode {
    /**
     * A footstep is gone once it is older than the time limit or has been pushed past the
     * step limit, whichever happens first. It fades according to whichever limit is closer.
     */
    COMBINED,

    /**
     * Footsteps decay after a set duration in seconds.
     */
    TIME,

    /**
     * Only keep the last N position changes (steps).
     */
    STEPS;

    /** Whether the time limit applies in this mode. */
    public boolean usesTime() {
        return this != STEPS;
    }

    /** Whether the step limit applies in this mode. */
    public boolean usesSteps() {
        return this != TIME;
    }

    /** The next mode in the settings cycle. */
    public TrackingMode next() {
        TrackingMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /**
     * How far a footstep is towards expiring, from 0 (fresh) to 1 (gone).
     *
     * @param ageFraction  age divided by the time limit
     * @param stepFraction how many steps ago it was made, divided by the step limit
     */
    public float progress(float ageFraction, float stepFraction) {
        float age = usesTime() ? ageFraction : 0f;
        float steps = usesSteps() ? stepFraction : 0f;
        return Math.min(1f, Math.max(0f, Math.max(age, steps)));
    }
}
