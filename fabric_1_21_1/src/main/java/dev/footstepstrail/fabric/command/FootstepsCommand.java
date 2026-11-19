package dev.footstepstrail.fabric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.footstepstrail.command.FootstepsHelp;
import dev.footstepstrail.config.FootstepShape;
import dev.footstepstrail.config.FootstepType;
import dev.footstepstrail.config.FootstepsConfig;
import dev.footstepstrail.config.TrackingMode;
import dev.footstepstrail.tracking.FootstepsTracker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Registers and handles the /footsteps command (Fabric 1.20.1).
 */
@Environment(EnvType.CLIENT)
public class FootstepsCommand {

    private static final String[] COLOR_OPTIONS = new String[] {
            "white",
            "gray",
            "red",
            "green",
            "blue",
            "yellow"
    };

    private static final String[] SHAPE_OPTIONS = new String[] {
            "square",
            "circle",
            "paw",
            "cross",
    };

    private static final String[] TYPE_OPTIONS = new String[] {
            "terrain",
            "surface",
            "classic",
    };

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher,
            CommandRegistryAccess registryAccess) {
        // /footsteps appearance is offered only for classic footsteps; keep suggestions in step.
        FootstepsConfig.setTypeChangeListener(CommandSuggestionsRefresher::refresh);
        dispatcher.register(
                ClientCommandManager.literal("footsteps")
                        // /footsteps - show status
                        .executes(context -> executeStatus(context.getSource()))

                        // /footsteps toggle
                        .then(ClientCommandManager.literal("toggle")
                                .executes(context -> executeToggle(context.getSource())))

                        // /footsteps type <terrain|surface|classic>
                        .then(ClientCommandManager.literal("type")
                                .then(ClientCommandManager.argument("type", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (String option : TYPE_OPTIONS) {
                                                builder.suggest(option);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> executeType(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "type")))))

                        // /footsteps mode <combined|time|steps>
                        .then(ClientCommandManager.literal("mode")
                                .then(ClientCommandManager.argument("mode", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            builder.suggest("combined");
                                            builder.suggest("time");
                                            builder.suggest("steps");
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> executeMode(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "mode")))))

                        // /footsteps config time set <seconds>
                        // /footsteps config steps set <count>
                        .then(ClientCommandManager.literal("config")
                                .then(ClientCommandManager.literal("time")
                                        .then(ClientCommandManager.literal("set")
                                                .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(1, 300))
                                                        .executes(context -> executeDuration(
                                                                context.getSource(),
                                                                IntegerArgumentType.getInteger(context, "seconds"))))))
                                .then(ClientCommandManager.literal("steps")
                                        .then(ClientCommandManager.literal("set")
                                                .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(1, 500))
                                                        .executes(context -> executeSteps(
                                                                context.getSource(),
                                                                IntegerArgumentType.getInteger(context, "count"))))))
                                .then(ClientCommandManager.literal("rotation")
                                        .then(ClientCommandManager.argument("enabled", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                                .executes(context -> executeRotation(
                                                        context.getSource(),
                                                        com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "enabled")))))
                                .then(ClientCommandManager.literal("alternating")
                                        .then(ClientCommandManager.argument("enabled", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                                .executes(context -> executeAlternating(
                                                        context.getSource(),
                                                        com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "enabled")))))
                                .then(ClientCommandManager.literal("stealth")
                                        .then(ClientCommandManager.argument("enabled", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                                .executes(context -> executeStealth(
                                                        context.getSource(),
                                                        com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "enabled")))))
                                .then(ClientCommandManager.literal("scuffs")
                                        .then(ClientCommandManager.argument("enabled", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                                .executes(context -> executeScuffs(
                                                        context.getSource(),
                                                        com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "enabled"))))))

                        // /footsteps size <value>
                        .then(ClientCommandManager.literal("size")
                                .then(ClientCommandManager.argument("value", FloatArgumentType.floatArg(0.05f, 2.0f))
                                        .executes(context -> executeSize(
                                                context.getSource(),
                                                FloatArgumentType.getFloat(context, "value")))))

                        // /footsteps appearance shape|color|glow  (only while type is classic)
                        .then(ClientCommandManager.literal("appearance")
                                        .requires(src -> FootstepsConfig.getInstance().isClassic())
                                .then(ClientCommandManager.literal("glow")
                                        .executes(context -> executeGlow(context.getSource(),
                                                !FootstepsConfig.getInstance().isGlow()))
                                        .then(ClientCommandManager.argument("enabled", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                                .executes(context -> executeGlow(
                                                        context.getSource(),
                                                        com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "enabled")))))
                                .then(ClientCommandManager.literal("shape")
                                        .then(ClientCommandManager.argument("shape", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    for (String option : SHAPE_OPTIONS) {
                                                        builder.suggest(option);
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> executeShape(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "shape")))))
                                .then(ClientCommandManager.literal("color")
                                        .then(ClientCommandManager.argument("color", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    for (String option : COLOR_OPTIONS) {
                                                        builder.suggest(option);
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> executeColor(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "color"))))))

                        // /footsteps help
                        .then(ClientCommandManager.literal("help")
                                .executes(context -> executeHelp(context.getSource())))

                        // /footsteps clear
                        .then(ClientCommandManager.literal("clear")
                                .executes(context -> executeClear(context.getSource())))

                        // /footsteps status
                        .then(ClientCommandManager.literal("status")
                                .executes(context -> executeStatus(context.getSource()))));
    }

    private static int executeToggle(FabricClientCommandSource source) {
        FootstepsConfig config = FootstepsConfig.getInstance();
        config.toggle();
        config.save();

        String status = config.isEnabled() ? "enabled" : "disabled";
        Formatting color = config.isEnabled() ? Formatting.GREEN : Formatting.RED;

        source.sendFeedback(Text.translatable("text.footstepstrail.command.toggle",
                Text.literal(status).formatted(color)));

        if (!config.isEnabled()) {
            FootstepsTracker.getInstance().clear();
        }

        return 1;
    }

    private static int executeMode(FabricClientCommandSource source, String modeStr) {
        FootstepsConfig config = FootstepsConfig.getInstance();

        TrackingMode mode;
        try {
            mode = TrackingMode.valueOf(modeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendError(Text.translatable("text.footstepstrail.command.invalid_mode")
                    .formatted(Formatting.RED));
            return 0;
        }

        config.setTrackingMode(mode);
        config.save();
        FootstepsTracker.getInstance().clear();

        source.sendFeedback(Text.translatable("text.footstepstrail.command.mode",
                Text.literal(mode.name().toLowerCase()).formatted(Formatting.AQUA)));

        return 1;
    }

    private static int executeDuration(FabricClientCommandSource source, int seconds) {
        FootstepsConfig config = FootstepsConfig.getInstance();
        config.setMaxDurationSeconds(seconds);
        config.save();

        source.sendFeedback(Text.translatable("text.footstepstrail.command.duration",
                Text.literal(String.valueOf(seconds)).formatted(Formatting.AQUA)));

        return 1;
    }

    private static int executeSteps(FabricClientCommandSource source, int count) {
        FootstepsConfig config = FootstepsConfig.getInstance();
        config.setMaxSteps(count);
        config.save();

        source.sendFeedback(Text.translatable("text.footstepstrail.command.steps",
                Text.literal(String.valueOf(count)).formatted(Formatting.AQUA)));

        return 1;
    }

    private static int executeColor(FabricClientCommandSource source, String colorName) {
        FootstepsConfig config = FootstepsConfig.getInstance();

        String normalized = colorName.toLowerCase();
        switch (normalized) {
            case "white":
                config.setColor(1.0f, 1.0f, 1.0f);
                break;
            case "gray":
            case "grey":
                config.setColor(0.7f, 0.7f, 0.7f);
                normalized = "gray";
                break;
            case "red":
                config.setColor(1.0f, 0.2f, 0.2f);
                break;
            case "green":
                config.setColor(0.2f, 1.0f, 0.2f);
                break;
            case "blue":
                config.setColor(0.2f, 0.4f, 1.0f);
                break;
            case "yellow":
                config.setColor(1.0f, 1.0f, 0.2f);
                break;
            default:
                source.sendError(Text.translatable("text.footstepstrail.command.invalid_color")
                        .formatted(Formatting.RED));
                return 0;
        }

        config.save();
        source.sendFeedback(Text.translatable("text.footstepstrail.command.color",
                Text.literal(normalized).formatted(Formatting.AQUA))
                .formatted(Formatting.GREEN));
        return 1;
    }

    private static int executeSize(FabricClientCommandSource source, float value) {
        FootstepsConfig config = FootstepsConfig.getInstance();
        config.setMarkerSize(value);
        config.save();

        source.sendFeedback(Text.translatable("text.footstepstrail.command.size",
                Text.literal(String.format("%.2f", value)).formatted(Formatting.AQUA)));
        return 1;
    }

    private static int executeType(FabricClientCommandSource source, String typeStr) {
        FootstepType type;
        try {
            type = FootstepType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendError(Text.translatable("text.footstepstrail.command.invalid_type")
                    .formatted(Formatting.RED));
            return 0;
        }

        FootstepsConfig config = FootstepsConfig.getInstance();
        config.setType(type);
        config.save();
        FootstepsTracker.getInstance().clear();

        source.sendFeedback(Text.translatable("text.footstepstrail.command.type",
                Text.literal(type.name().toLowerCase()).formatted(Formatting.AQUA)));
        return 1;
    }

    private static int executeGlow(FabricClientCommandSource source, boolean enabled) {
        FootstepsConfig config = FootstepsConfig.getInstance();
        config.setGlow(enabled);
        config.save();

        source.sendFeedback(Text.translatable("text.footstepstrail.command.glow",
                Text.literal(enabled ? "enabled" : "disabled")
                        .formatted(enabled ? Formatting.GREEN : Formatting.RED)));
        return 1;
    }

    private static int executeShape(FabricClientCommandSource source, String shapeStr) {
        FootstepsConfig config = FootstepsConfig.getInstance();

        FootstepShape shape;
        try {
            shape = FootstepShape.valueOf(shapeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendError(Text.translatable("text.footstepstrail.command.invalid_shape")
                    .formatted(Formatting.RED));
            return 0;
        }

        config.setShape(shape);
        config.save();

        source.sendFeedback(Text.translatable("text.footstepstrail.command.shape",
                Text.literal(shape.name().toLowerCase()).formatted(Formatting.AQUA))
                .formatted(Formatting.GREEN));
        return 1;
    }

    private static int executeClear(FabricClientCommandSource source) {
        FootstepsTracker.getInstance().clear();
        source.sendFeedback(
                Text.translatable("text.footstepstrail.command.clear").formatted(Formatting.YELLOW));
        return 1;
    }

    private static int executeHelp(FabricClientCommandSource source) {
            source.sendFeedback(Text.translatable("text.footstepstrail.help.title").formatted(Formatting.GOLD));
            for (String[] entry : FootstepsHelp.ENTRIES) {
                source.sendFeedback(Text.literal(FootstepsHelp.command(entry)).formatted(Formatting.AQUA)
                        .append(Text.literal(FootstepsHelp.description(entry)).formatted(Formatting.GRAY)));
            }
            return 1;
    }

    private static int executeStatus(FabricClientCommandSource source) {
        FootstepsConfig config = FootstepsConfig.getInstance();
        FootstepsTracker tracker = FootstepsTracker.getInstance();

        String enabledStr = config.isEnabled() ? "Enabled" : "Disabled";
        Formatting enabledColor = config.isEnabled() ? Formatting.GREEN : Formatting.RED;

        source.sendFeedback(Text.translatable("text.footstepstrail.status.title").formatted(Formatting.GOLD));
        source.sendFeedback(Text.translatable("text.footstepstrail.status.status",
                Text.literal(enabledStr).formatted(enabledColor)));
        source.sendFeedback(Text.translatable("text.footstepstrail.status.type",
                Text.literal(config.getType().name().toLowerCase()).formatted(Formatting.AQUA)));
        if (config.isTerrainUnsupported()) {
            source.sendFeedback(Text.translatable("text.footstepstrail.status.terrain_fallback",
                    dev.footstepstrail.deformation.TerrainSupport.unsupportedRenderer()).formatted(Formatting.YELLOW));
        }
        source.sendFeedback(Text.translatable("text.footstepstrail.status.mode",
                Text.literal(config.getTrackingMode().name().toLowerCase())
                        .formatted(Formatting.AQUA)));
        if (config.isClassic()) {
            source.sendFeedback(Text.translatable("text.footstepstrail.status.shape",
                    Text.literal(config.getShape().name().toLowerCase()).formatted(Formatting.AQUA)));
            source.sendFeedback(Text.translatable("text.footstepstrail.status.glow",
                    Text.literal(config.isGlow() ? "Enabled" : "Disabled")
                            .formatted(config.isGlow() ? Formatting.GREEN : Formatting.RED)));
        }

        if (config.getTrackingMode().usesTime()) {
            source.sendFeedback(Text.translatable("text.footstepstrail.status.duration",
                    Text.literal(String.valueOf(config.getMaxDurationSeconds()))
                            .formatted(Formatting.AQUA)));
        }
        if (config.getTrackingMode().usesSteps()) {
            source.sendFeedback(Text.translatable("text.footstepstrail.status.steps",
                    Text.literal(String.valueOf(config.getMaxSteps())).formatted(Formatting.AQUA)));
        }

        source.sendFeedback(Text.translatable("text.footstepstrail.status.color",
                (int) (config.getColorR() * 255),
                (int) (config.getColorG() * 255),
                (int) (config.getColorB() * 255)).formatted(Formatting.AQUA));

        source.sendFeedback(Text.translatable("text.footstepstrail.status.size",
                config.getMarkerSize()).formatted(Formatting.AQUA));

        source.sendFeedback(Text.translatable("text.footstepstrail.status.opacity",
                config.getMaxOpacity()).formatted(Formatting.AQUA));
        source.sendFeedback(Text.translatable("text.footstepstrail.status.rotation",
                config.isEnableRotation()).formatted(Formatting.AQUA));
        source.sendFeedback(Text.translatable("text.footstepstrail.status.alternating",
                config.isEnableAlternatingFeet()).formatted(Formatting.AQUA));
        source.sendFeedback(Text.translatable("text.footstepstrail.status.stealth",
                config.isStealthSneak()).formatted(Formatting.AQUA));
        source.sendFeedback(Text.translatable("text.footstepstrail.status.scuffs",
                config.isHardSurfaceScuffs()).formatted(Formatting.AQUA));

        source.sendFeedback(Text.translatable("text.footstepstrail.status.active",
                tracker.getCount()).formatted(Formatting.YELLOW));

        return 1;
    }

    private static int executeRotation(FabricClientCommandSource source, boolean enabled) {
        FootstepsConfig config = FootstepsConfig.getInstance();
        config.setEnableRotation(enabled);
        config.save();
        source.sendFeedback(Text.translatable("text.footstepstrail.command.rotation",
                Text.literal(String.valueOf(enabled)).formatted(Formatting.AQUA)));
        return 1;
    }

    private static int executeAlternating(FabricClientCommandSource source, boolean enabled) {
        FootstepsConfig config = FootstepsConfig.getInstance();
        config.setEnableAlternatingFeet(enabled);
        config.save();
        source.sendFeedback(Text.translatable("text.footstepstrail.command.alternating",
                Text.literal(String.valueOf(enabled)).formatted(Formatting.AQUA)));
        return 1;
    }

    private static int executeStealth(FabricClientCommandSource source, boolean enabled) {
        FootstepsConfig config = FootstepsConfig.getInstance();
        config.setStealthSneak(enabled);
        config.save();
        source.sendFeedback(Text.translatable("text.footstepstrail.command.stealth",
                Text.literal(String.valueOf(enabled)).formatted(Formatting.AQUA)));
        return 1;
    }

    private static int executeScuffs(FabricClientCommandSource source, boolean enabled) {
        FootstepsConfig config = FootstepsConfig.getInstance();
        config.setHardSurfaceScuffs(enabled);
        config.save();
        source.sendFeedback(Text.translatable("text.footstepstrail.command.scuffs",
                Text.literal(String.valueOf(enabled)).formatted(Formatting.AQUA)));
        return 1;
    }
}
