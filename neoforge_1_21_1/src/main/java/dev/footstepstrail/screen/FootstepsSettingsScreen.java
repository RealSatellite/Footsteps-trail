package dev.footstepstrail.screen;

import dev.footstepstrail.config.FootstepColors;
import dev.footstepstrail.config.FootstepShape;
import dev.footstepstrail.config.FootstepType;
import dev.footstepstrail.config.FootstepsConfig;
import dev.footstepstrail.config.TrackingMode;
import dev.footstepstrail.tracking.FootstepsTracker;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * In-game settings for Footsteps Trail, opened from Mod Menu (Fabric) or the
 * mods list (NeoForge). Every option here is also reachable through
 * /footsteps commands.
 */
public class FootstepsSettingsScreen extends Screen {

    private static final String KEY = "text.footstepstrail.settings.";
    private static final int WIDGET_WIDTH = 150;

    private final Screen parent;
    private final FootstepsConfig config = FootstepsConfig.getInstance();
    private final FootstepType typeOnOpen = config.getType();
    private final TrackingMode modeOnOpen = config.getTrackingMode();
    private HeaderAndFooterLayout layout;

    public FootstepsSettingsScreen(Screen parent) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout = new HeaderAndFooterLayout(this);
        layout.addTitleHeader(title, font);

        GridLayout grid = new GridLayout().columnSpacing(8).rowSpacing(2);
        GridLayout.RowHelper rows = grid.createRowHelper(2);

        rows.addChild(toggle("enabled", config::isEnabled, value -> {
            config.setEnabled(value);
            if (!value) {
                FootstepsTracker.getInstance().clear();
            }
        }));
        Button type = cycle("type", () -> name("type", config.getType()), () -> {
            FootstepType[] all = FootstepType.values();
            config.setType(all[(config.getType().ordinal() + 1) % all.length]);
            // Classic-only options switch on and off with the type.
            rebuildWidgets();
        });
        type.setTooltip(Tooltip.create(config.isTerrainUnsupported()
                ? Component.translatable(KEY + "terrain_fallback", dev.footstepstrail.deformation.TerrainSupport.unsupportedRenderer())
                : Component.translatable(KEY + "type.tooltip")));
        rows.addChild(type);

        rows.addChild(cycle("mode", () -> name("mode", config.getTrackingMode()), () -> {
            config.setTrackingMode(config.getTrackingMode().next());
            // The limit slider below changes meaning with the mode.
            rebuildWidgets();
        }));
        if (config.getTrackingMode().usesTime()) {
            rows.addChild(slider(1, 300, 1, config::getMaxDurationSeconds,
                    v -> config.setMaxDurationSeconds((int) v),
                    v -> Component.translatable(KEY + "duration", (int) v)));
        }
        if (config.getTrackingMode().usesSteps()) {
            rows.addChild(slider(1, 500, 1, config::getMaxSteps,
                    v -> config.setMaxSteps((int) v),
                    v -> Component.translatable(KEY + "steps", (int) v)));
        }

        rows.addChild(slider(0.05, 2.0, 0.05, config::getMarkerSize,
                v -> config.setMarkerSize((float) v),
                v -> Component.translatable(KEY + "size", String.format(Locale.ROOT, "%.2f", v))));
        rows.addChild(slider(0.05, 1.0, 0.05, config::getMaxOpacity,
                v -> config.setMaxOpacity((float) v),
                v -> Component.translatable(KEY + "opacity", Math.round(v * 100) + "%")));

        rows.addChild(slider(8, 256, 8, config::getMaxRenderDistance,
                config::setMaxRenderDistance,
                v -> Component.translatable(KEY + "distance", (int) v)));
        rows.addChild(toggle("rotation", config::isEnableRotation, config::setEnableRotation));

        rows.addChild(toggle("alternating", config::isEnableAlternatingFeet, config::setEnableAlternatingFeet));
        rows.addChild(toggle("stealth", config::isStealthSneak, config::setStealthSneak));

        rows.addChild(toggle("scuffs", config::isHardSurfaceScuffs, config::setHardSurfaceScuffs));
        rows.addChild(classicOnly(toggle("glow", config::isGlow, config::setGlow)));

        rows.addChild(classicOnly(cycle("shape", () -> name("shape", config.getShape()), () -> {
            FootstepShape[] all = FootstepShape.values();
            config.setShape(all[(config.getShape().ordinal() + 1) % all.length]);
        })));
        rows.addChild(classicOnly(cycle("color", this::colorName, () -> FootstepColors.cycle(config))));

        layout.addToContents(grid);
        layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).width(200).build());
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
    }

    @Override
    public void onClose() {
        config.save();
        // Footprints made with another type or expiry rule would look wrong.
        if (config.getType() != typeOnOpen || config.getTrackingMode() != modeOnOpen) {
            FootstepsTracker.getInstance().clear();
        }
        minecraft.setScreen(parent);
    }

    private <T extends AbstractWidget> T classicOnly(T widget) {
        if (!config.isClassic()) {
            widget.active = false;
            widget.setTooltip(Tooltip.create(Component.translatable(KEY + "classic_only")));
        }
        return widget;
    }

    private Component colorName() {
        int index = FootstepColors.indexOf(config);
        return Component.translatable(KEY + "color." + (index < 0 ? "custom" : FootstepColors.NAMES[index]));
    }

    private static Component name(String option, Enum<?> value) {
        return Component.translatable(KEY + option + "." + value.name().toLowerCase(Locale.ROOT));
    }

    private static Component label(String option, Component value) {
        return Component.translatable(KEY + option).append(": ").append(value);
    }

    private static Button cycle(String option, java.util.function.Supplier<Component> value, Runnable next) {
        return Button.builder(label(option, value.get()), button -> {
            next.run();
            button.setMessage(label(option, value.get()));
        }).width(WIDGET_WIDTH).build();
    }

    private static Button toggle(String option, java.util.function.BooleanSupplier get,
            java.util.function.Consumer<Boolean> set) {
        return cycle(option,
                () -> Component.translatable(KEY + (get.getAsBoolean() ? "on" : "off")),
                () -> set.accept(!get.getAsBoolean()));
    }

    private static AbstractSliderButton slider(double min, double max, double step, DoubleSupplier get,
            DoubleConsumer set, DoubleFunction<Component> message) {
        return new RangeSlider(min, max, step, get.getAsDouble(), set, message);
    }

    /** A slider over [min, max] that snaps to step. */
    private static final class RangeSlider extends AbstractSliderButton {
        private final double min;
        private final double max;
        private final double step;
        private final DoubleConsumer set;
        private final DoubleFunction<Component> message;

        RangeSlider(double min, double max, double step, double current, DoubleConsumer set,
                DoubleFunction<Component> message) {
            super(0, 0, WIDGET_WIDTH, 20, Component.empty(), (clamp(current, min, max) - min) / (max - min));
            this.min = min;
            this.max = max;
            this.step = step;
            this.set = set;
            this.message = message;
            updateMessage();
        }

        private double actual() {
            double raw = min + value * (max - min);
            return clamp(Math.round(raw / step) * step, min, max);
        }

        @Override
        protected void updateMessage() {
            if (message != null) {
                setMessage(message.apply(actual()));
            }
        }

        @Override
        protected void applyValue() {
            set.accept(actual());
        }

        private static double clamp(double v, double lo, double hi) {
            return Math.max(lo, Math.min(hi, v));
        }
    }
}
