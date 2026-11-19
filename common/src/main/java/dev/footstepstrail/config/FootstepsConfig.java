package dev.footstepstrail.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.footstepstrail.FootstepsTrail;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import dev.footstepstrail.deformation.DeformationMaterial;
import dev.footstepstrail.deformation.TerrainSupport;

/**
 * Configuration holder for the Footsteps Trail mod.
 */
public class FootstepsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "footstepstrail.json";
    private static FootstepsConfig INSTANCE;
    private static volatile Runnable typeChangeListener;

    private static Path getConfigPath() {
        return resolveConfigDir().resolve(CONFIG_FILE_NAME);
    }

    /**
     * Resolve the platform config directory without hard depending on Fabric or
     * Forge APIs.
     *
     * <p>
     * This keeps the common code usable on both loaders without an abstraction
     * library.
     * </p>
     */
    private static Path resolveConfigDir() {
        // Fabric
        try {
            Class<?> fabricLoaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object fabricLoader = fabricLoaderClass.getMethod("getInstance").invoke(null);
            Object configDir = fabricLoaderClass.getMethod("getConfigDir").invoke(fabricLoader);
            if (configDir instanceof Path) {
                return (Path) configDir;
            }
        } catch (Throwable ignored) {
            // Not on Fabric (or Fabric loader not present)
        }

        // Forge
        try {
            Class<?> fmlPathsClass = Class.forName("net.minecraftforge.fml.loading.FMLPaths");
            Object configDirProvider = fmlPathsClass.getField("CONFIGDIR").get(null);
            Object configDir = configDirProvider.getClass().getMethod("get").invoke(configDirProvider);
            if (configDir instanceof Path) {
                return (Path) configDir;
            }
        } catch (Throwable ignored) {
            // Not on Forge (or Forge classes not present)
        }

        // Legacy Forge (1.12.2)
        // net.minecraftforge.fml.common.Loader#instance().getConfigDir() ->
        // java.io.File
        try {
            Class<?> loaderClass = Class.forName("net.minecraftforge.fml.common.Loader");
            Object loader = loaderClass.getMethod("instance").invoke(null);
            Object configDirFile = loaderClass.getMethod("getConfigDir").invoke(loader);
            if (configDirFile instanceof java.io.File) {
                return ((java.io.File) configDirFile).toPath();
            }
        } catch (Throwable ignored) {
            // Not on legacy Forge
        }

        // Fallback (dev/test)
        return Paths.get("config");
    }

    // Whether the footsteps trail is enabled
    private boolean enabled = true;

    // The tracking mode (COMBINED, TIME or STEPS)
    private TrackingMode trackingMode = TrackingMode.COMBINED;

    // Maximum duration in seconds for TIME and COMBINED modes
    private int maxDurationSeconds = 60;

    // Maximum number of steps for STEPS and COMBINED modes
    private int maxSteps = 50;

    // Minimum distance (in blocks) before recording a new step
    private double minDistanceForStep = 1.0;

    // Visuals
    private FootstepType type = FootstepType.TERRAIN;
    // True once the player picked a type. Until then the default follows the renderer.
    private boolean typeChosen = false;
    // Pre-1.4 setting (true meant terrain depth); read once to migrate, never written.
    private Boolean realistic;
    private float markerSize = 0.2f;
    private float colorR = 0.7f;
    private float colorG = 0.7f;
    private float colorB = 0.7f;
    private float maxOpacity = 0.6f;
    private FootstepShape shape = FootstepShape.SQUARE;
    // Classic footsteps only: soft halo and brightened colour.
    private boolean glow = false;

    // Whether hard surfaces (stone, planks) still show a faint dust scuff.
    private boolean hardSurfaceScuffs = true;

    // Beyond this distance (in blocks) footprints are not rendered.
    private double maxRenderDistance = 48.0;

    // Optional block registry ID -> mechanical material, for modded/resource-pack surfaces.
    private Map<String, String> deformationMaterials = new HashMap<>();

    public DeformationMaterial deformationMaterial(String blockId) {
        if (deformationMaterials == null) return null;
        String name = deformationMaterials.get(blockId);
        if (name == null) return null;
        return DeformationMaterial.byName(name);
    }

    // New configuration variables
    private boolean enableRotation = true;
    private boolean enableAlternatingFeet = true;
    private float alternatingFootOffset = 0.15f;
    private boolean stealthSneak = true;

    private FootstepsConfig() {
    }

    public static FootstepsConfig getInstance() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public void save() {
        Path configPath = getConfigPath();
        try {
            Files.createDirectories(configPath.getParent());
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Write to a temp file first so a crash mid-write never leaves a
        // half-written config behind.
        Path tempPath = configPath.resolveSibling(CONFIG_FILE_NAME + ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(tempPath)) {
                GSON.toJson(this, writer);
            }
            try {
                Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicNotSupported) {
                Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void load() {
        Path configPath = getConfigPath();
        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                INSTANCE = GSON.fromJson(reader, FootstepsConfig.class);
                if (INSTANCE != null) {
                    if (INSTANCE.migrate()) {
                        INSTANCE.save();
                    }
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JsonParseException e) {
                // A hand-edited file with a typo must not crash the game or be
                // silently replaced. Keep a copy, then run on defaults.
                Path backup = configPath.resolveSibling(CONFIG_FILE_NAME + ".broken");
                try {
                    Files.copy(configPath, backup, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                    // Best effort only.
                }
                FootstepsTrail.LOGGER.warning("Could not read " + configPath + " (" + e.getMessage()
                        + "). Using default settings; a copy was saved as " + backup.getFileName() + ".");
                INSTANCE = new FootstepsConfig();
                return;
            }
        }
        INSTANCE = new FootstepsConfig();
        INSTANCE.save();
    }

    /**
     * Fill in settings from older config files. Unknown enum names (such as the
     * removed REALISTIC shape) load as null through Gson.
     *
     * @return true when something changed and the file should be rewritten
     */
    boolean migrate() {
        boolean changed = false;
        // Only pre-1.4 files contain "realistic" (it is never written back). Gson
        // runs the constructor first, so type already holds the default here.
        // 1.3.x: realistic=true drew terrain dents, false drew flat shapes.
        if (realistic != null) {
            type = realistic ? FootstepType.TERRAIN : FootstepType.CLASSIC;
            realistic = null;
            changed = true;
        }
        if (type == null) {
            type = FootstepType.TERRAIN;
            changed = true;
        }
        if (shape == null) {
            shape = FootstepShape.SQUARE;
            changed = true;
        }
        if (trackingMode == null) {
            trackingMode = TrackingMode.COMBINED;
            changed = true;
        }
        // Hand-edited values skip the setters, so bring them back into range.
        // The file itself is left alone.
        setMaxDurationSeconds(maxDurationSeconds);
        setMaxSteps(maxSteps);
        setMinDistanceForStep(minDistanceForStep);
        setMarkerSize(markerSize);
        setColor(colorR, colorG, colorB);
        setMaxOpacity(maxOpacity);
        setMaxRenderDistance(maxRenderDistance);
        setAlternatingFootOffset(alternatingFootOffset);
        return changed;
    }

    // Getters and Setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void toggle() {
        this.enabled = !this.enabled;
    }

    public TrackingMode getTrackingMode() {
        return trackingMode;
    }

    public void setTrackingMode(TrackingMode trackingMode) {
        this.trackingMode = trackingMode;
    }

    public int getMaxDurationSeconds() {
        return maxDurationSeconds;
    }

    public void setMaxDurationSeconds(int maxDurationSeconds) {
        this.maxDurationSeconds = Math.max(1, Math.min(300, maxDurationSeconds));
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
        this.maxSteps = Math.max(1, Math.min(500, maxSteps));
    }

    public double getMinDistanceForStep() {
        return minDistanceForStep;
    }

    public void setMinDistanceForStep(double minDistanceForStep) {
        this.minDistanceForStep = Math.max(0.1, Math.min(5.0, minDistanceForStep));
    }

    public float getMarkerSize() {
        return markerSize;
    }

    public void setMarkerSize(float markerSize) {
        this.markerSize = Math.max(0.05f, Math.min(2.0f, markerSize));
    }

    public float getColorR() {
        return colorR;
    }

    public float getColorG() {
        return colorG;
    }

    public float getColorB() {
        return colorB;
    }

    public void setColor(float r, float g, float b) {
        this.colorR = Math.max(0, Math.min(1, r));
        this.colorG = Math.max(0, Math.min(1, g));
        this.colorB = Math.max(0, Math.min(1, b));
    }

    public float getMaxOpacity() {
        return maxOpacity;
    }

    public void setMaxOpacity(float maxOpacity) {
        this.maxOpacity = Math.max(0.05f, Math.min(1.0f, maxOpacity));
    }

    /**
     * The type in effect. Until the player picks one, the default is terrain, or surface when the
     * installed renderer cannot draw terrain footprints. A type the player picked is always used
     * as is, so the settings never show one type while drawing another.
     */
    public FootstepType getType() {
        FootstepType stored = type != null ? type : FootstepType.TERRAIN;
        if (!typeChosen && stored == FootstepType.TERRAIN && !TerrainSupport.available()) {
            return FootstepType.SURFACE;
        }
        return stored;
    }

    /** The player's choice; from now on it is used even when terrain cannot be drawn. */
    public void setType(FootstepType type) {
        if (type != null) {
            FootstepType before = getType();
            this.type = type;
            this.typeChosen = true;
            Runnable listener = typeChangeListener;
            if (listener != null && getType() != before) {
                listener.run();
            }
        }
    }

    /**
     * Runs whenever the type changes, from a command or the settings screen. Loaders use it to
     * rebuild command suggestions, since some commands are only offered for one type.
     */
    public static void setTypeChangeListener(Runnable listener) {
        typeChangeListener = listener;
    }

    /** Footprints are pressed into the terrain mesh. */
    public boolean isTerrain() {
        return getType() == FootstepType.TERRAIN;
    }

    /** Block-aware overlay footprints. */
    public boolean isSurface() {
        return getType() == FootstepType.SURFACE;
    }

    /** The player chose terrain, but the installed renderer cannot draw it, so none appear. */
    public boolean isTerrainUnsupported() {
        return getType() == FootstepType.TERRAIN && !TerrainSupport.available();
    }

    /** Flat shape/colour markers. */
    public boolean isClassic() {
        return getType() == FootstepType.CLASSIC;
    }

    public FootstepShape getShape() {
        return shape != null ? shape : FootstepShape.SQUARE;
    }

    public boolean isGlow() {
        return glow;
    }

    public void setGlow(boolean glow) {
        this.glow = glow;
    }

    public boolean isHardSurfaceScuffs() {
        return hardSurfaceScuffs;
    }

    public void setHardSurfaceScuffs(boolean hardSurfaceScuffs) {
        this.hardSurfaceScuffs = hardSurfaceScuffs;
    }

    public double getMaxRenderDistance() {
        return maxRenderDistance;
    }

    public void setMaxRenderDistance(double maxRenderDistance) {
        this.maxRenderDistance = Math.max(8.0, Math.min(256.0, maxRenderDistance));
    }

    public void setShape(FootstepShape shape) {
        if (shape != null) {
            this.shape = shape;
        }
    }

    public boolean isEnableRotation() {
        return enableRotation;
    }

    public void setEnableRotation(boolean enableRotation) {
        this.enableRotation = enableRotation;
    }

    public boolean isEnableAlternatingFeet() {
        return enableAlternatingFeet;
    }

    public void setEnableAlternatingFeet(boolean enableAlternatingFeet) {
        this.enableAlternatingFeet = enableAlternatingFeet;
    }

    public float getAlternatingFootOffset() {
        return alternatingFootOffset;
    }

    public void setAlternatingFootOffset(float alternatingFootOffset) {
        this.alternatingFootOffset = Math.max(0.0f, Math.min(1.0f, alternatingFootOffset));
    }

    public boolean isStealthSneak() {
        return stealthSneak;
    }

    public void setStealthSneak(boolean stealthSneak) {
        this.stealthSneak = stealthSneak;
    }
}
