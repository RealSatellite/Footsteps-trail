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
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

/**
 * Registers and handles the /footsteps command.
 */
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
                        "cross"
        };

        private static final String[] TYPE_OPTIONS = new String[] {
                        "terrain",
                        "surface",
                        "classic"
        };

        public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher,
                        CommandBuildContext registryAccess) {
                // /footsteps appearance is offered only for classic footsteps; keep suggestions in step.
                FootstepsConfig.setTypeChangeListener(CommandSuggestionsRefresher::refresh);
                dispatcher.register(
                                LiteralArgumentBuilder.<FabricClientCommandSource>literal("footsteps")
                                                // /footsteps - show status
                                                .executes(context -> executeStatus(context.getSource()))

                                                // /footsteps toggle
                                                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("toggle")
                                                                .executes(context -> executeToggle(
                                                                                context.getSource())))

                                                // /footsteps type <terrain|surface|classic>
                                                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("type")
                                                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
                                                                                "type", StringArgumentType.word())
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
                                                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mode")
                                                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("mode",
                                                                                                StringArgumentType
                                                                                                                .word())
                                                                                .suggests((context, builder) -> {
                                                                                        builder.suggest("combined");
                                                                                        builder.suggest("time");
                                                                                        builder.suggest("steps");
                                                                                        return builder.buildFuture();
                                                                                })
                                                                                .executes(context -> executeMode(
                                                                                                context.getSource(),
                                                                                                StringArgumentType
                                                                                                                .getString(context,
                                                                                                                                "mode")))))

                                                // /footsteps config time set <seconds>
                                                // /footsteps config steps set <count>
                                                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("config")
                                                                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("time")
                                                                                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("set")
                                                                                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("seconds",
                                                                                                                                IntegerArgumentType
                                                                                                                                                .integer(1, 300))
                                                                                                                .executes(context -> executeDuration(
                                                                                                                                context.getSource(),
                                                                                                                                IntegerArgumentType
                                                                                                                                                .getInteger(context,
                                                                                                                                                                "seconds"))))))
                                                                 .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("steps")
                                                                                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("set")
                                                                                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("count",
                                                                                                                                IntegerArgumentType
                                                                                                                                                .integer(1, 500))
                                                                                                                .executes(context -> executeSteps(
                                                                                                                                context.getSource(),
                                                                                                                                IntegerArgumentType
                                                                                                                                                .getInteger(context,
                                                                                                                                                                "count"))))))
                                                                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("rotation")
                                                                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Boolean>argument("enabled",
                                                                                                com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                .bool())
                                                                                                .executes(context -> executeRotation(
                                                                                                                context.getSource(),
                                                                                                                com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                                .getBool(context,
                                                                                                                                                "enabled")))))
                                                                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("alternating")
                                                                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Boolean>argument("enabled",
                                                                                                com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                .bool())
                                                                                                .executes(context -> executeAlternating(
                                                                                                                context.getSource(),
                                                                                                                com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                                .getBool(context,
                                                                                                                                                "enabled")))))
                                                                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("stealth")
                                                                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Boolean>argument("enabled",
                                                                                                com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                .bool())
                                                                                                .executes(context -> executeStealth(
                                                                                                                context.getSource(),
                                                                                                                com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                                .getBool(context,
                                                                                                                                                "enabled")))))
                                                                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("scuffs")
                                                                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Boolean>argument("enabled",
                                                                                                com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                .bool())
                                                                                                .executes(context -> executeScuffs(
                                                                                                                context.getSource(),
                                                                                                                com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                                .getBool(context,
                                                                                                                                                "enabled"))))))

                                                // /footsteps size <value>
                                                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("size")
                                                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Float>argument("value",
                                                                                                FloatArgumentType
                                                                                                                .floatArg(0.05f, 2.0f))
                                                                                .executes(context -> executeSize(
                                                                                                context.getSource(),
                                                                                                FloatArgumentType
                                                                                                                .getFloat(context,
                                                                                                                                "value")))))

                                                // /footsteps appearance shape|color|glow  (only while type is classic)
                                                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("appearance")
                                                                .requires(src -> FootstepsConfig.getInstance().isClassic())
                                                                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("glow")
                                                                                .executes(context -> executeGlow(context.getSource(),
                                                                                                !FootstepsConfig.getInstance().isGlow()))
                                                                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Boolean>argument("enabled",
                                                                                                com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                                                                                .executes(context -> executeGlow(
                                                                                                                context.getSource(),
                                                                                                                com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                                .getBool(context, "enabled")))))
                                                                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("shape")
                                                                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
                                                                                                "shape",
                                                                                                StringArgumentType
                                                                                                                .word())
                                                                                                .suggests((context,
                                                                                                                builder) -> {
                                                                                                        for (String option : SHAPE_OPTIONS) {
                                                                                                                builder.suggest(option);
                                                                                                        }
                                                                                                        return builder.buildFuture();
                                                                                                })
                                                                                                .executes(context -> executeShape(
                                                                                                                context.getSource(),
                                                                                                                StringArgumentType
                                                                                                                                .getString(context,
                                                                                                                                                "shape")))))
                                                                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color")
                                                                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("color",
                                                                                                                StringArgumentType
                                                                                                                                .word())
                                                                                                .suggests((context,
                                                                                                                builder) -> {
                                                                                                        for (String option : COLOR_OPTIONS) {
                                                                                                                builder.suggest(option);
                                                                                                        }
                                                                                                        return builder.buildFuture();
                                                                                                })
                                                                                                .executes(context -> executeColor(
                                                                                                                context.getSource(),
                                                                                                                StringArgumentType
                                                                                                                                .getString(context,
                                                                                                                                                "color"))))))

                                                // /footsteps help
                                                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("help")
                                                                .executes(context -> executeHelp(
                                                                                context.getSource())))

                                                // /footsteps clear
                                                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear")
                                                                .executes(context -> executeClear(context.getSource())))

                                                // /footsteps status
                                                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status")
                                                                .executes(context -> executeStatus(
                                                                                context.getSource()))));
        }

        private static int executeToggle(FabricClientCommandSource source) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.toggle();
                config.save();

                String status = config.isEnabled() ? "enabled" : "disabled";
                ChatFormatting color = config.isEnabled() ? ChatFormatting.GREEN : ChatFormatting.RED;

                source.sendFeedback(Component.translatable("text.footstepstrail.command.toggle",
                                Component.literal(status).withStyle(color)));

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
                        source.sendError(Component.translatable("text.footstepstrail.command.invalid_mode")
                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }

                config.setTrackingMode(mode);
                config.save();
                FootstepsTracker.getInstance().clear();

                source.sendFeedback(Component.translatable("text.footstepstrail.command.mode",
                                Component.literal(mode.name().toLowerCase()).withStyle(ChatFormatting.AQUA)));

                return 1;
        }

        private static int executeDuration(FabricClientCommandSource source, int seconds) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setMaxDurationSeconds(seconds);
                config.save();

                source.sendFeedback(Component.translatable("text.footstepstrail.command.duration",
                                Component.literal(String.valueOf(seconds)).withStyle(ChatFormatting.AQUA)));

                return 1;
        }

        private static int executeSteps(FabricClientCommandSource source, int count) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setMaxSteps(count);
                config.save();

                source.sendFeedback(Component.translatable("text.footstepstrail.command.steps",
                                Component.literal(String.valueOf(count)).withStyle(ChatFormatting.AQUA)));

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
                                source.sendError(Component.translatable("text.footstepstrail.command.invalid_color")
                                                .withStyle(ChatFormatting.RED));
                                return 0;
                }

                config.save();
                source.sendFeedback(Component.translatable("text.footstepstrail.command.color",
                                Component.literal(normalized).withStyle(ChatFormatting.AQUA))
                                .withStyle(ChatFormatting.GREEN));
                return 1;
        }

        private static int executeSize(FabricClientCommandSource source, float value) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setMarkerSize(value);
                config.save();

                source.sendFeedback(Component.translatable("text.footstepstrail.command.size",
                                Component.literal(String.format("%.2f", value)).withStyle(ChatFormatting.AQUA)));
                return 1;
        }

        private static int executeType(FabricClientCommandSource source, String typeStr) {
                FootstepType type;
                try {
                        type = FootstepType.valueOf(typeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                        source.sendError(Component.translatable("text.footstepstrail.command.invalid_type")
                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }

                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setType(type);
                config.save();
                FootstepsTracker.getInstance().clear();

                source.sendFeedback(Component.translatable("text.footstepstrail.command.type",
                                Component.literal(type.name().toLowerCase()).withStyle(ChatFormatting.AQUA)));
                return 1;
        }

        private static int executeGlow(FabricClientCommandSource source, boolean enabled) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setGlow(enabled);
                config.save();

                source.sendFeedback(Component.translatable("text.footstepstrail.command.glow",
                                Component.literal(enabled ? "enabled" : "disabled")
                                                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)));
                return 1;
        }

        private static int executeShape(FabricClientCommandSource source, String shapeStr) {
                FootstepsConfig config = FootstepsConfig.getInstance();

                FootstepShape shape;
                try {
                        shape = FootstepShape.valueOf(shapeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                        source.sendError(Component.translatable("text.footstepstrail.command.invalid_shape")
                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }

                config.setShape(shape);
                config.save();

                source.sendFeedback(Component.translatable("text.footstepstrail.command.shape",
                                Component.literal(shape.name().toLowerCase()).withStyle(ChatFormatting.AQUA))
                                .withStyle(ChatFormatting.GREEN));
                return 1;
        }

        private static int executeClear(FabricClientCommandSource source) {
                FootstepsTracker.getInstance().clear();
                source.sendFeedback(
                                Component.translatable("text.footstepstrail.command.clear").withStyle(ChatFormatting.YELLOW));
                return 1;
        }

        private static int executeHelp(FabricClientCommandSource source) {
                source.sendFeedback(Component.translatable("text.footstepstrail.help.title").withStyle(ChatFormatting.GOLD));
                for (String[] entry : FootstepsHelp.ENTRIES) {
                    source.sendFeedback(Component.literal(FootstepsHelp.command(entry)).withStyle(ChatFormatting.AQUA)
                            .append(Component.literal(FootstepsHelp.description(entry)).withStyle(ChatFormatting.GRAY)));
                }
                return 1;
        }

        private static int executeStatus(FabricClientCommandSource source) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                FootstepsTracker tracker = FootstepsTracker.getInstance();

                String enabledStr = config.isEnabled() ? "Enabled" : "Disabled";
                ChatFormatting enabledColor = config.isEnabled() ? ChatFormatting.GREEN : ChatFormatting.RED;

                source.sendFeedback(Component.translatable("text.footstepstrail.status.title").withStyle(ChatFormatting.GOLD));
                source.sendFeedback(Component.translatable("text.footstepstrail.status.status",
                                Component.literal(enabledStr).withStyle(enabledColor)));
                source.sendFeedback(Component.translatable("text.footstepstrail.status.type",
                                Component.literal(config.getType().name().toLowerCase()).withStyle(ChatFormatting.AQUA)));
                if (config.isTerrainUnsupported()) {
                        source.sendFeedback(Component.translatable("text.footstepstrail.status.terrain_fallback",
                                        dev.footstepstrail.deformation.TerrainSupport.unsupportedRenderer()).withStyle(ChatFormatting.YELLOW));
                }
                source.sendFeedback(Component.translatable("text.footstepstrail.status.mode",
                                Component.literal(config.getTrackingMode().name().toLowerCase())
                                                .withStyle(ChatFormatting.AQUA)));
                if (config.isClassic()) {
                        source.sendFeedback(Component.translatable("text.footstepstrail.status.shape",
                                        Component.literal(config.getShape().name().toLowerCase()).withStyle(ChatFormatting.AQUA)));
                        source.sendFeedback(Component.translatable("text.footstepstrail.status.glow",
                                        Component.literal(config.isGlow() ? "Enabled" : "Disabled")
                                                        .withStyle(config.isGlow() ? ChatFormatting.GREEN : ChatFormatting.RED)));
                }

                if (config.getTrackingMode().usesTime()) {
                        source.sendFeedback(Component.translatable("text.footstepstrail.status.duration",
                                        Component.literal(String.valueOf(config.getMaxDurationSeconds()))
                                                        .withStyle(ChatFormatting.AQUA)));
                }
                if (config.getTrackingMode().usesSteps()) {
                        source.sendFeedback(Component.translatable("text.footstepstrail.status.steps",
                                        Component.literal(String.valueOf(config.getMaxSteps())).withStyle(ChatFormatting.AQUA)));
                }

                source.sendFeedback(Component.translatable("text.footstepstrail.status.color",
                                (int) (config.getColorR() * 255),
                                (int) (config.getColorG() * 255),
                                (int) (config.getColorB() * 255)).withStyle(ChatFormatting.AQUA));

                source.sendFeedback(Component.translatable("text.footstepstrail.status.size",
                                config.getMarkerSize()).withStyle(ChatFormatting.AQUA));

                source.sendFeedback(Component.translatable("text.footstepstrail.status.opacity",
                                config.getMaxOpacity()).withStyle(ChatFormatting.AQUA));

                source.sendFeedback(Component.translatable("text.footstepstrail.status.rotation",
                                config.isEnableRotation()).withStyle(ChatFormatting.AQUA));
                source.sendFeedback(Component.translatable("text.footstepstrail.status.alternating",
                                config.isEnableAlternatingFeet()).withStyle(ChatFormatting.AQUA));
                source.sendFeedback(Component.translatable("text.footstepstrail.status.stealth",
                                config.isStealthSneak()).withStyle(ChatFormatting.AQUA));
                source.sendFeedback(Component.translatable("text.footstepstrail.status.scuffs",
                                config.isHardSurfaceScuffs()).withStyle(ChatFormatting.AQUA));

                source.sendFeedback(Component.translatable("text.footstepstrail.status.active",
                                tracker.getCount()).withStyle(ChatFormatting.YELLOW));

                return 1;
        }

        private static int executeRotation(FabricClientCommandSource source, boolean enabled) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setEnableRotation(enabled);
                config.save();
                source.sendFeedback(Component.translatable("text.footstepstrail.command.rotation",
                                Component.literal(String.valueOf(enabled)).withStyle(ChatFormatting.AQUA)));
                return 1;
        }

        private static int executeAlternating(FabricClientCommandSource source, boolean enabled) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setEnableAlternatingFeet(enabled);
                config.save();
                source.sendFeedback(Component.translatable("text.footstepstrail.command.alternating",
                                Component.literal(String.valueOf(enabled)).withStyle(ChatFormatting.AQUA)));
                return 1;
        }

        private static int executeStealth(FabricClientCommandSource source, boolean enabled) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setStealthSneak(enabled);
                config.save();
                source.sendFeedback(Component.translatable("text.footstepstrail.command.stealth",
                                Component.literal(String.valueOf(enabled)).withStyle(ChatFormatting.AQUA)));
                return 1;
        }

        private static int executeScuffs(FabricClientCommandSource source, boolean enabled) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setHardSurfaceScuffs(enabled);
                config.save();
                source.sendFeedback(Component.translatable("text.footstepstrail.command.scuffs",
                                Component.literal(String.valueOf(enabled)).withStyle(ChatFormatting.AQUA)));
                return 1;
        }
}
