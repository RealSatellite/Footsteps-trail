package dev.footstepstrail.screen;

import dev.footstepstrail.config.FootstepColors;
import dev.footstepstrail.config.FootstepShape;
import dev.footstepstrail.config.FootstepType;
import dev.footstepstrail.config.FootstepsConfig;
import dev.footstepstrail.config.TrackingMode;
import dev.footstepstrail.tracking.FootstepsTracker;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.client.config.GuiSlider;
import org.lwjgl.input.Keyboard;

/**
 * In-game settings for Footsteps Trail (1.12.2), opened from the Forge mods
 * list. Every option here is also reachable through /footsteps commands.
 */
public class FootstepsSettingsScreen extends GuiScreen {

    private static final String KEY = "text.footstepstrail.settings.";
    private static final int WIDGET_WIDTH = 150;
    private static final int DONE_ID = 999;

    private interface Label {
        String get();
    }

    private interface DoubleSetter {
        void set(double value);
    }

    private interface DoubleLabel {
        String get(double value);
    }

    private final GuiScreen parent;
    private final FootstepsConfig config = FootstepsConfig.getInstance();
    private final FootstepType typeOnOpen = config.getType();
    private final TrackingMode modeOnOpen = config.getTrackingMode();

    private final Map<Integer, Runnable> actions = new HashMap<>();
    private final Map<Integer, Label> labels = new HashMap<>();
    private final List<GuiButton> classicButtons = new ArrayList<>();
    private int nextId;

    public FootstepsSettingsScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        actions.clear();
        labels.clear();
        classicButtons.clear();
        nextId = 0;

        toggle("enabled", config::isEnabled, () -> {
            config.toggle();
            if (!config.isEnabled()) {
                FootstepsTracker.getInstance().clear();
            }
        }, false);
        cycle("type", () -> name("type", config.getType()), () -> {
            FootstepType[] all = FootstepType.values();
            config.setType(all[(config.getType().ordinal() + 1) % all.length]);
            // Classic-only options switch on and off with the type.
            initGui();
        }, false);

        cycle("mode", () -> name("mode", config.getTrackingMode()), () -> {
            config.setTrackingMode(config.getTrackingMode().next());
            // The limit slider below changes meaning with the mode.
            initGui();
        }, false);
        if (config.getTrackingMode().usesTime()) {
            slider(1, 300, 1, config.getMaxDurationSeconds(), v -> config.setMaxDurationSeconds((int) v),
                    v -> I18n.format(KEY + "duration", (int) v));
        }
        if (config.getTrackingMode().usesSteps()) {
            slider(1, 500, 1, config.getMaxSteps(), v -> config.setMaxSteps((int) v),
                    v -> I18n.format(KEY + "steps", (int) v));
        }

        slider(0.05, 2.0, 0.05, config.getMarkerSize(), v -> config.setMarkerSize((float) v),
                v -> I18n.format(KEY + "size", String.format(Locale.ROOT, "%.2f", v)));
        slider(0.05, 1.0, 0.05, config.getMaxOpacity(), v -> config.setMaxOpacity((float) v),
                v -> I18n.format(KEY + "opacity", Math.round(v * 100) + "%"));

        slider(8, 256, 8, config.getMaxRenderDistance(), config::setMaxRenderDistance,
                v -> I18n.format(KEY + "distance", (int) v));
        toggle("rotation", config::isEnableRotation,
                () -> config.setEnableRotation(!config.isEnableRotation()), false);

        toggle("alternating", config::isEnableAlternatingFeet,
                () -> config.setEnableAlternatingFeet(!config.isEnableAlternatingFeet()), false);
        toggle("stealth", config::isStealthSneak,
                () -> config.setStealthSneak(!config.isStealthSneak()), false);

        toggle("scuffs", config::isHardSurfaceScuffs,
                () -> config.setHardSurfaceScuffs(!config.isHardSurfaceScuffs()), false);
        toggle("glow", config::isGlow, () -> config.setGlow(!config.isGlow()), true);

        cycle("shape", () -> name("shape", config.getShape()), () -> {
            FootstepShape[] all = FootstepShape.values();
            config.setShape(all[(config.getShape().ordinal() + 1) % all.length]);
        }, true);
        cycle("color", this::colorName, () -> FootstepColors.cycle(config), true);

        int rows = (nextId + 1) / 2;
        buttonList.add(new GuiButton(DONE_ID, width / 2 - 100, rowY(rows) + 8, 200, 20, I18n.format("gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == DONE_ID) {
            close();
            return;
        }
        Runnable action = actions.get(button.id);
        if (action == null) {
            return;
        }
        action.run();
        Label label = labels.get(button.id);
        if (label != null) {
            button.displayString = label.get();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            close();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, I18n.format(KEY + "title"), width / 2, 15, 0xFFFFFF);
        if (config.isTerrainUnsupported()) {
            drawCenteredString(fontRenderer, I18n.format(KEY + "terrain_fallback", dev.footstepstrail.deformation.TerrainSupport.unsupportedRenderer()),
                    width / 2, height - 14, 0xFFFF55);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        for (GuiButton button : classicButtons) {
            if (!button.enabled && button.isMouseOver()) {
                drawHoveringText(fontRenderer.listFormattedStringToWidth(I18n.format(KEY + "classic_only"), 200),
                        mouseX, mouseY);
            }
        }
    }

    private void close() {
        config.save();
        // Footprints made with another type or expiry rule would look wrong.
        if (config.getType() != typeOnOpen || config.getTrackingMode() != modeOnOpen) {
            FootstepsTracker.getInstance().clear();
        }
        mc.displayGuiScreen(parent);
    }

    private int rowY(int row) {
        return 32 + row * 22;
    }

    private int nextX(int id) {
        return id % 2 == 0 ? width / 2 - 155 : width / 2 + 5;
    }

    private void cycle(String option, Label value, Runnable next, boolean classicOnly) {
        int id = nextId++;
        Label label = () -> I18n.format(KEY + option) + ": " + value.get();
        GuiButton button = new GuiButton(id, nextX(id), rowY(id / 2), WIDGET_WIDTH, 20, label.get());
        if (classicOnly) {
            button.enabled = config.isClassic();
            classicButtons.add(button);
        }
        buttonList.add(button);
        actions.put(id, next);
        labels.put(id, label);
    }

    private void toggle(String option, java.util.function.BooleanSupplier get, Runnable flip, boolean classicOnly) {
        cycle(option, () -> I18n.format(KEY + (get.getAsBoolean() ? "on" : "off")), flip, classicOnly);
    }

    private void slider(double min, double max, double step, double current, DoubleSetter set, DoubleLabel message) {
        int id = nextId++;
        GuiSlider.ISlider handler = slider -> {
            double snapped = Math.max(min, Math.min(max, Math.round(slider.getValue() / step) * step));
            set.set(snapped);
            slider.displayString = message.get(snapped);
        };
        GuiSlider slider = new GuiSlider(id, nextX(id), rowY(id / 2), WIDGET_WIDTH, 20, "", "",
                min, max, Math.max(min, Math.min(max, current)), true, false, handler);
        buttonList.add(slider);
    }

    private String colorName() {
        int index = FootstepColors.indexOf(config);
        return I18n.format(KEY + "color." + (index < 0 ? "custom" : FootstepColors.NAMES[index]));
    }

    private static String name(String option, Enum<?> value) {
        return I18n.format(KEY + option + "." + value.name().toLowerCase(Locale.ROOT));
    }
}
