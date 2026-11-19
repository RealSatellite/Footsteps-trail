package dev.footstepstrail;

import java.util.logging.Logger;

/**
 * Shared mod constants usable from both Fabric and Forge loader modules.
 */
public final class FootstepsTrail {
    public static final String MOD_ID = "footstepstrail";
    /**
     * Keep logging dependency-free so the common module can run on legacy
     * environments like Forge 1.12.2 (Java 8) without pulling SLF4J.
     */
    public static final Logger LOGGER = Logger.getLogger(MOD_ID);

    private FootstepsTrail() {
    }
}
