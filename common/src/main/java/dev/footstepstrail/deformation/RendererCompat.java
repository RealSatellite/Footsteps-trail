package dev.footstepstrail.deformation;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides at client startup whether terrain footprints can be drawn with the installed renderer.
 *
 * <p>Each loader passes what it knows about installed mods and the hooks it ships. A hook is a
 * mixin into a renderer's chunk builder; its mixin plugin reports {@link #attached(String)} once
 * Mixin has applied it. Loading the target class here makes Mixin apply it straight away.
 */
public final class RendererCompat {
    public interface Environment {
        boolean modLoaded(String id);
        /** Whether a class file is present, without loading it. */
        boolean hasClass(String name);
        /** Loads (without initializing) a class so Mixin transforms it. */
        void load(String name) throws Throwable;
    }

    /** A renderer mod this build can hook. */
    public static final class Hook {
        final String modId, name, target;
        public Hook(String modId, String name, String target) {
            this.modId = modId; this.name = name; this.target = target;
        }
    }

    /** Renderers that replace chunk meshing and that no hook supports yet. */
    private static final String[][] UNSUPPORTED = {
            {"vulkanmod", "VulkanMod"},
            {"nothirium", "Nothirium"},
            {"vintagium", "Vintagium"},
            {"celeritas", "Celeritas"},
            {"rubidium", "Rubidium"},
            {"magnesium", "Magnesium"},
    };

    private static final Set<String> attached = ConcurrentHashMap.newKeySet();

    /** Called by mixin plugins after a renderer hook was applied to its target class. */
    public static void attached(String targetClass) {
        attached.add(targetClass);
    }

    /** Whether the hook into {@code targetClass} has been applied. */
    public static boolean isAttached(String targetClass) {
        return attached.contains(targetClass);
    }

    public static void detect(Environment env, Hook... hooks) {
        if (env.modLoaded("optifine") || env.modLoaded("optifabric") || env.hasClass("net.optifine.Config")
                || env.hasClass("optifine.OptiFineForgeTweaker")) {
            TerrainSupport.unsupported("OptiFine");
            return;
        }
        for (Hook hook : hooks) {
            if (!env.modLoaded(hook.modId)) continue;
            try {
                env.load(hook.target);
            } catch (Throwable ignored) {
                // A missing or changed class is reported as unsupported below.
            }
            if (attached.contains(hook.target)) TerrainSupport.hooked(hook.name);
            else TerrainSupport.unsupported("this version of " + hook.name);
            return;
        }
        for (String[] mod : UNSUPPORTED) {
            if (env.modLoaded(mod[0])) {
                TerrainSupport.unsupported(mod[1]);
                return;
            }
        }
        // Sodium-family renderers this build has no hook for at all.
        for (String id : new String[] {"sodium", "embeddium"}) {
            if (env.modLoaded(id)) {
                TerrainSupport.unsupported(id.equals("sodium") ? "Sodium" : "Embeddium");
                return;
            }
        }
    }

    private RendererCompat() {}
}
