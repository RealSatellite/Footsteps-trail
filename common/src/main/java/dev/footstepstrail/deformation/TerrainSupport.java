package dev.footstepstrail.deformation;

import dev.footstepstrail.FootstepsTrail;

/**
 * Whether terrain footprints can reach the chunk mesh with the renderer that is installed.
 *
 * <p>Terrain mode edits block quads while chunks are built. Renderer mods such as Sodium,
 * Embeddium and OptiFine replace that code. Each loader checks at startup which renderer is
 * active and whether a compatibility hook attached to it. When none did, the default footprint
 * type becomes surface; a player who still picks terrain is told that none will appear.
 */
public final class TerrainSupport {
    private static volatile String renderer = "vanilla";
    private static volatile String unsupported;

    /** True when terrain footprints are drawn into the chunk mesh. */
    public static boolean available() {
        return unsupported == null;
    }

    /** The renderer that prevents terrain footprints, or null when they work. */
    public static String unsupportedRenderer() {
        return unsupported;
    }

    /** The renderer terrain footprints are drawn with. */
    public static String renderer() {
        return renderer;
    }

    /** A compatibility hook attached to this renderer. */
    public static void hooked(String name) {
        renderer = name;
        unsupported = null;
        FootstepsTrail.LOGGER.info("Terrain footprints are using the " + name + " chunk renderer hook");
    }

    /** This renderer replaces chunk building and no hook attached; terrain cannot be drawn. */
    public static void unsupported(String name) {
        renderer = name;
        unsupported = name;
        FootstepsTrail.LOGGER.warning("Terrain footprints are not supported with " + name
                + "; surface footprints are the default instead");
    }

    private TerrainSupport() {}
}
