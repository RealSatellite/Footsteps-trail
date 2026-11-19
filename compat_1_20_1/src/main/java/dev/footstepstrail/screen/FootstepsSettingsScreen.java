package dev.footstepstrail.screen;

import dev.footstepstrail.config.FootstepColors;
import dev.footstepstrail.config.FootstepShape;
import dev.footstepstrail.config.FootstepType;
import dev.footstepstrail.config.FootstepsConfig;
import dev.footstepstrail.config.TrackingMode;
import dev.footstepstrail.deformation.TerrainSupport;
import dev.footstepstrail.tracking.FootstepsTracker;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.GridWidget;
import net.minecraft.client.gui.widget.SimplePositioningWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/**
 * In-game settings for Footsteps Trail (1.20.1), opened from Mod Menu (Fabric)
 * or the Forge mods list. Every option here is also reachable through
 * /footsteps commands.
 */
public class FootstepsSettingsScreen extends Screen {

    private static final String KEY = "text.footstepstrail.settings.";
    private static final int WIDGET_WIDTH = 150;

    private final Screen parent;
    private final FootstepsConfig config = FootstepsConfig.getInstance();
    private final FootstepType typeOnOpen = config.getType();
    private final TrackingMode modeOnOpen = config.getTrackingMode();

    public FootstepsSettingsScreen(Screen parent) {
        super(Text.translatable(KEY + "title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        GridWidget grid = new GridWidget().setColumnSpacing(8).setRowSpacing(2);
        GridWidget.Adder rows = grid.createAdder(2);

        rows.add(toggle("enabled", config::isEnabled, value -> {
            config.setEnabled(value);
            if (!value) {
                FootstepsTracker.getInstance().clear();
            }
        }));
        ButtonWidget type = cycle("type", () -> name("type", config.getType()), () -> {
            FootstepType[] all = FootstepType.values();
            config.setType(all[(config.getType().ordinal() + 1) % all.length]);
            // Classic-only options switch on and off with the type.
            clearAndInit();
        });
        type.setTooltip(Tooltip.of(Text.translatable(KEY + "type.tooltip")));
        rows.add(type);

        rows.add(cycle("mode", () -> name("mode", config.getTrackingMode()), () -> {
            config.setTrackingMode(config.getTrackingMode().next());
            // The limit slider below changes meaning with the mode.
            clearAndInit();
        }));
        if (config.getTrackingMode().usesTime()) {
            rows.add(slider(1, 300, 1, config::getMaxDurationSeconds,
                    v -> config.setMaxDurationSeconds((int) v),
                    v -> Text.translatable(KEY + "duration", (int) v)));
        }
        if (config.getTrackingMode().usesSteps()) {
            rows.add(slider(1, 500, 1, config::getMaxSteps,
                    v -> config.setMaxSteps((int) v),
                    v -> Text.translatable(KEY + "steps", (int) v)));
        }

        rows.add(slider(0.05, 2.0, 0.05, config::getMarkerSize,
                v -> config.setMarkerSize((float) v),
                v -> Text.translatable(KEY + "size", String.format(Locale.ROOT, "%.2f", v))));
        rows.add(slider(0.05, 1.0, 0.05, config::getMaxOpacity,
                v -> config.setMaxOpacity((float) v),
                v -> Text.translatable(KEY + "opacity", Math.round(v * 100) + "%")));

        rows.add(slider(8, 256, 8, config::getMaxRenderDistance,
                config::setMaxRenderDistance,
                v -> Text.translatable(KEY + "distance", (int) v)));
        rows.add(toggle("rotation", config::isEnableRotation, config::setEnableRotation));

        rows.add(toggle("alternating", config::isEnableAlternatingFeet, config::setEnableAlternatingFeet));
        rows.add(toggle("stealth", config::isStealthSneak, config::setStealthSneak));

        rows.add(toggle("scuffs", config::isHardSurfaceScuffs, config::setHardSurfaceScuffs));
        rows.add(classicOnly(toggle("glow", config::isGlow, config::setGlow)));

        rows.add(classicOnly(cycle("shape", () -> name("shape", config.getShape()), () -> {
            FootstepShape[] all = FootstepShape.values();
            config.setShape(all[(config.getShape().ordinal() + 1) % all.length]);
        })));
        rows.add(classicOnly(cycle("color", this::colorName, () -> FootstepColors.cycle(config))));

        rows.add(ButtonWidget.builder(ScreenTexts.DONE, button -> close()).width(200).build(),
                2, rows.copyPositioner().alignHorizontalCenter().marginTop(8));

        grid.refreshPositions();
        SimplePositioningWidget.setPos(grid, 0, 32, width, height - 32, 0.5f, 0.0f);
        grid.forEachChild(this::addDrawableChild);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 15, 0xFFFFFF);
        if (config.isTerrainUnsupported()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable(KEY + "terrain_fallback",
                    TerrainSupport.unsupportedRenderer()), width / 2, height - 14, 0xFFFF55);
        }
    }

    @Override
    public void close() {
        config.save();
        // Footprints made with another type or expiry rule would look wrong.
        if (config.getType() != typeOnOpen || config.getTrackingMode() != modeOnOpen) {
            FootstepsTracker.getInstance().clear();
        }
        client.setScreen(parent);
    }

    private <T extends ClickableWidget> T classicOnly(T widget) {
        if (!config.isClassic()) {
            widget.active = false;
            widget.setTooltip(Tooltip.of(Text.translatable(KEY + "classic_only")));
        }
        return widget;
    }

    private Text colorName() {
        int index = FootstepColors.indexOf(config);
        return Text.translatable(KEY + "color." + (index < 0 ? "custom" : FootstepColors.NAMES[index]));
    }

    private static Text name(String option, Enum<?> value) {
        return Text.translatable(KEY + option + "." + value.name().toLowerCase(Locale.ROOT));
    }

    private static Text label(String option, Text value) {
        return Text.translatable(KEY + option).append(": ").append(value);
    }

    private static ButtonWidget cycle(String option, Supplier<Text> value, Runnable next) {
        return ButtonWidget.builder(label(option, value.get()), button -> {
            next.run();
            button.setMessage(label(option, value.get()));
        }).width(WIDGET_WIDTH).build();
    }

    private static ButtonWidget toggle(String option, BooleanSupplier get, Consumer<Boolean> set) {
        return cycle(option,
                () -> Text.translatable(KEY + (get.getAsBoolean() ? "on" : "off")),
                () -> set.accept(!get.getAsBoolean()));
    }

    private static SliderWidget slider(double min, double max, double step, DoubleSupplier get,
            DoubleConsumer set, DoubleFunction<Text> message) {
        return new RangeSlider(min, max, step, get.getAsDouble(), set, message);
    }

    /** A slider over [min, max] that snaps to step. */
    private static final class RangeSlider extends SliderWidget {
        private final double min;
        private final double max;
        private final double step;
        private final DoubleConsumer set;
        private final DoubleFunction<Text> message;

        RangeSlider(double min, double max, double step, double current, DoubleConsumer set,
                DoubleFunction<Text> message) {
            super(0, 0, WIDGET_WIDTH, 20, Text.empty(), (clamp(current, min, max) - min) / (max - min));
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
