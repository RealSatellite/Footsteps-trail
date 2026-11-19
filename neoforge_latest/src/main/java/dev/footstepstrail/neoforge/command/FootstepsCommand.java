package dev.footstepstrail.neoforge.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.footstepstrail.command.FootstepsHelp;
import dev.footstepstrail.config.FootstepShape;
import dev.footstepstrail.config.FootstepType;
import dev.footstepstrail.config.FootstepsConfig;
import dev.footstepstrail.config.TrackingMode;
import dev.footstepstrail.tracking.FootstepsTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * Registers and handles the /footsteps client command on NeoForge.
 */
public final class FootstepsCommand {

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

        private FootstepsCommand() {
        }

        public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
                // /footsteps appearance is offered only for classic footsteps; keep suggestions in step.
                FootstepsConfig.setTypeChangeListener(CommandSuggestionsRefresher::refresh);
                dispatcher.register(
                                Commands.literal("footsteps")
                                                 .executes(ctx -> executeStatus(ctx.getSource()))

                                                 .then(Commands.literal("toggle")
                                                                 .executes(ctx -> executeToggle(ctx.getSource())))

                                                 // /footsteps type <terrain|surface|classic>
                                                 .then(Commands.literal("type")
                                                                 .then(Commands.argument("type",
                                                                                 StringArgumentType.word())
                                                                                 .suggests((context, builder) -> SharedSuggestionProvider
                                                                                                 .suggest(TYPE_OPTIONS, builder))
                                                                                 .executes(ctx -> executeType(
                                                                                                 ctx.getSource(),
                                                                                                 StringArgumentType.getString(ctx, "type")))))

                                                 .then(Commands.literal("mode")
                                                                 .then(Commands.argument("mode",
                                                                                 StringArgumentType.word())
                                                                                 .suggests((context,
                                                                                                 builder) -> SharedSuggestionProvider
                                                                                                                 .suggest(
                                                                                                                                 new String[] { "combined", "time",
                                                                                                                                                 "steps" },
                                                                                                                                 builder))
                                                                                 .executes(ctx -> executeMode(
                                                                                                 ctx.getSource(),
                                                                                                 StringArgumentType
                                                                                                                 .getString(ctx, "mode")))))

                                                 .then(Commands.literal("config")
                                                                 .then(Commands.literal("time")
                                                                                 .then(Commands.literal("set")
                                                                                                 .then(Commands.argument(
                                                                                                                 "seconds",
                                                                                                                 IntegerArgumentType
                                                                                                                                 .integer(1, 300))
                                                                                                                 .executes(ctx -> executeDuration(
                                                                                                                                 ctx.getSource(),
                                                                                                                                 IntegerArgumentType
                                                                                                                                                 .getInteger(ctx, "seconds"))))))
                                                                 .then(Commands.literal("steps")
                                                                                 .then(Commands.literal("set")
                                                                                                 .then(Commands.argument(
                                                                                                                 "count",
                                                                                                                 IntegerArgumentType
                                                                                                                                 .integer(1, 500))
                                                                                                                 .executes(ctx -> executeSteps(
                                                                                                                                 ctx.getSource(),
                                                                                                                                 IntegerArgumentType
                                                                                                                                                 .getInteger(ctx, "count"))))))
                                                                 .then(Commands.literal("rotation")
                                                                                 .then(Commands.argument("enabled",
                                                                                                 com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                 .bool())
                                                                                                 .executes(ctx -> executeRotation(
                                                                                                                 ctx.getSource(),
                                                                                                                 com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                                 .getBool(ctx, "enabled")))))
                                                                 .then(Commands.literal("alternating")
                                                                                 .then(Commands.argument("enabled",
                                                                                                 com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                 .bool())
                                                                                                 .executes(ctx -> executeAlternating(
                                                                                                                 ctx.getSource(),
                                                                                                                 com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                                 .getBool(ctx, "enabled")))))
                                                                 .then(Commands.literal("stealth")
                                                                                 .then(Commands.argument("enabled",
                                                                                                 com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                 .bool())
                                                                                                 .executes(ctx -> executeStealth(
                                                                                                                 ctx.getSource(),
                                                                                                                 com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                                 .getBool(ctx, "enabled")))))
                                                                 .then(Commands.literal("scuffs")
                                                                                 .then(Commands.argument("enabled",
                                                                                                 com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                 .bool())
                                                                                                 .executes(ctx -> executeScuffs(
                                                                                                                 ctx.getSource(),
                                                                                                                 com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                                 .getBool(ctx, "enabled"))))))

                                                 // /footsteps size <value>
                                                 .then(Commands.literal("size")
                                                                 .then(Commands.argument("value",
                                                                                 FloatArgumentType
                                                                                                 .floatArg(0.05f, 2.0f))
                                                                                 .executes(ctx -> executeSize(
                                                                                                 ctx.getSource(),
                                                                                                 FloatArgumentType
                                                                                                                 .getFloat(ctx, "value")))))

                                                 // /footsteps appearance shape|color|glow  (only while type is classic)
                                                 .then(Commands.literal("appearance")
                                                                 .requires(src -> FootstepsConfig.getInstance().isClassic())
                                                                 .then(Commands.literal("glow")
                                                                                 .executes(ctx -> executeGlow(ctx.getSource(),
                                                                                                 !FootstepsConfig.getInstance().isGlow()))
                                                                                 .then(Commands.argument("enabled",
                                                                                                 com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                                                                                 .executes(ctx -> executeGlow(
                                                                                                                 ctx.getSource(),
                                                                                                                 com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "enabled")))))
                                                                 .then(Commands.literal("shape")
                                                                                 .then(Commands.argument("shape",
                                                                                                 StringArgumentType
                                                                                                                 .word())
                                                                                                 .suggests((context,
                                                                                                                 builder) -> SharedSuggestionProvider
                                                                                                                                 .suggest(SHAPE_OPTIONS,
                                                                                                                                                 builder))
                                                                                                 .executes(ctx -> executeShape(
                                                                                                                 ctx.getSource(),
                                                                                                                 StringArgumentType
                                                                                                                                 .getString(ctx, "shape")))))
                                                                 .then(Commands.literal("color")
                                                                                 .then(Commands.argument("color",
                                                                                                 StringArgumentType
                                                                                                                 .word())
                                                                                                 .suggests((context,
                                                                                                                 builder) -> SharedSuggestionProvider
                                                                                                                                 .suggest(COLOR_OPTIONS,
                                                                                                                                                 builder))
                                                                                                 .executes(ctx -> executeColor(
                                                                                                                 ctx.getSource(),
                                                                                                                 StringArgumentType
                                                                                                                                 .getString(ctx, "color"))))))

                                                 // /footsteps help
                                                 .then(Commands.literal("help")
                                                                 .executes(ctx -> executeHelp(ctx.getSource())))

                                                 .then(Commands.literal("clear")
                                                                 .executes(ctx -> executeClear(ctx.getSource())))

                                                 .then(Commands.literal("status")
                                                                 .executes(ctx -> executeStatus(ctx.getSource()))));
        }

        private static void sendChat(CommandSourceStack source, Component message) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                        mc.player.sendSystemMessage(message);
                } else {
                        // Best-effort fallback; client commands should always have a player.
                        source.sendSuccess(() -> message, false);
                }
        }

        private static int executeToggle(CommandSourceStack source) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.toggle();
                config.save();

                String status = config.isEnabled() ? "enabled" : "disabled";
                ChatFormatting color = config.isEnabled() ? ChatFormatting.GREEN : ChatFormatting.RED;

                sendChat(source, Component.translatable("text.footstepstrail.command.toggle",
                                Component.literal(status).withStyle(color)).withStyle(ChatFormatting.GREEN));

                if (!config.isEnabled()) {
                        FootstepsTracker.getInstance().clear();
                }

                return 1;
        }

        private static int executeMode(CommandSourceStack source, String modeStr) {
                FootstepsConfig config = FootstepsConfig.getInstance();

                // Convert mode string to enum
                TrackingMode mode;
                try {
                        mode = TrackingMode.valueOf(modeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                        sendChat(source, Component.translatable("text.footstepstrail.command.invalid_mode")
                                         .withStyle(ChatFormatting.RED));
                        return 0;
                }

                config.setTrackingMode(mode);
                config.save();
                FootstepsTracker.getInstance().clear();

                sendChat(source, Component.translatable("text.footstepstrail.command.mode",
                                Component.literal(mode.name().toLowerCase()).withStyle(ChatFormatting.AQUA)));

                return 1;
        }

        private static int executeDuration(CommandSourceStack source, int seconds) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setMaxDurationSeconds(seconds);
                config.save();

                sendChat(source, Component.translatable("text.footstepstrail.command.duration", seconds)
                                .withStyle(ChatFormatting.GREEN));

                return 1;
        }

        private static int executeSteps(CommandSourceStack source, int count) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setMaxSteps(count);
                config.save();

                sendChat(source, Component.translatable("text.footstepstrail.command.steps", count)
                                .withStyle(ChatFormatting.GREEN));

                return 1;
        }

        private static int executeColor(CommandSourceStack source, String colorName) {
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
                                sendChat(source, Component.translatable("text.footstepstrail.command.invalid_color")
                                                 .withStyle(ChatFormatting.RED));
                                return 0;
                }

                config.save();
                sendChat(source, Component.translatable("text.footstepstrail.command.color",
                                Component.literal(normalized).withStyle(ChatFormatting.AQUA))
                                .withStyle(ChatFormatting.GREEN));

                return 1;
        }

        private static int executeSize(CommandSourceStack source, float value) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setMarkerSize(value);
                config.save();

                sendChat(source, Component.translatable("text.footstepstrail.command.size", value)
                                .withStyle(ChatFormatting.GREEN));

                return 1;
        }

        private static int executeType(CommandSourceStack source, String typeStr) {
                FootstepType type;
                try {
                        type = FootstepType.valueOf(typeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                        sendChat(source, Component.translatable("text.footstepstrail.command.invalid_type")
                                        .withStyle(ChatFormatting.RED));
                        return 0;
                }

                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setType(type);
                config.save();
                FootstepsTracker.getInstance().clear();

                sendChat(source, Component.translatable("text.footstepstrail.command.type",
                                Component.literal(type.name().toLowerCase()).withStyle(ChatFormatting.AQUA))
                                .withStyle(ChatFormatting.GREEN));
                return 1;
        }

        private static int executeGlow(CommandSourceStack source, boolean enabled) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setGlow(enabled);
                config.save();

                sendChat(source, Component.translatable("text.footstepstrail.command.glow",
                                Component.literal(enabled ? "enabled" : "disabled")
                                                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED))
                                .withStyle(ChatFormatting.GREEN));
                return 1;
        }

        private static int executeShape(CommandSourceStack source, String shapeStr) {
                FootstepsConfig config = FootstepsConfig.getInstance();

                FootstepShape shape;
                try {
                        shape = FootstepShape.valueOf(shapeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                        sendChat(source, Component.translatable("text.footstepstrail.command.invalid_shape")
                                         .withStyle(ChatFormatting.RED));
                        return 0;
                }

                config.setShape(shape);
                config.save();

                sendChat(source, Component.translatable("text.footstepstrail.command.shape",
                                Component.literal(shape.name().toLowerCase()).withStyle(ChatFormatting.AQUA))
                                .withStyle(ChatFormatting.GREEN));
                return 1;
        }

        private static int executeClear(CommandSourceStack source) {
                FootstepsTracker.getInstance().clear();
                sendChat(source, Component.translatable("text.footstepstrail.command.clear")
                                .withStyle(ChatFormatting.YELLOW));
                return 1;
        }

        private static int executeHelp(CommandSourceStack source) {
                sendChat(source, Component.translatable("text.footstepstrail.help.title").withStyle(ChatFormatting.GOLD));
                for (String[] entry : FootstepsHelp.ENTRIES) {
                    sendChat(source, Component.literal(FootstepsHelp.command(entry)).withStyle(ChatFormatting.AQUA)
                            .append(Component.literal(FootstepsHelp.description(entry)).withStyle(ChatFormatting.GRAY)));
                }
                return 1;
        }

        private static int executeStatus(CommandSourceStack source) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                FootstepsTracker tracker = FootstepsTracker.getInstance();

                String enabledStr = config.isEnabled() ? "Enabled" : "Disabled";
                ChatFormatting enabledColor = config.isEnabled() ? ChatFormatting.GREEN : ChatFormatting.RED;

                sendChat(source, Component.translatable("text.footstepstrail.status.title")
                                .withStyle(ChatFormatting.GOLD));

                sendChat(source, Component.translatable("text.footstepstrail.status.status",
                                Component.literal(enabledStr).withStyle(enabledColor)));

                sendChat(source, Component.translatable("text.footstepstrail.status.type",
                                Component.literal(config.getType().name().toLowerCase()).withStyle(ChatFormatting.AQUA)));
                if (config.isTerrainUnsupported()) {
                        sendChat(source, Component.translatable("text.footstepstrail.status.terrain_fallback",
                                        dev.footstepstrail.deformation.TerrainSupport.unsupportedRenderer()).withStyle(ChatFormatting.YELLOW));
                }

                sendChat(source, Component.translatable("text.footstepstrail.status.mode",
                                Component.literal(config.getTrackingMode().name().toLowerCase())
                                                .withStyle(ChatFormatting.AQUA)));

                if (config.isClassic()) {
                        sendChat(source, Component.translatable("text.footstepstrail.status.shape",
                                        Component.literal(config.getShape().name().toLowerCase())
                                                        .withStyle(ChatFormatting.AQUA)));
                        sendChat(source, Component.translatable("text.footstepstrail.status.glow",
                                        Component.literal(config.isGlow() ? "Enabled" : "Disabled")
                                                        .withStyle(config.isGlow() ? ChatFormatting.GREEN : ChatFormatting.RED)));
                }

                if (config.getTrackingMode().usesTime()) {
                        sendChat(source, Component.translatable("text.footstepstrail.status.duration",
                                        String.valueOf(config.getMaxDurationSeconds())).withStyle(ChatFormatting.AQUA));
                }
                if (config.getTrackingMode().usesSteps()) {
                        sendChat(source, Component.translatable("text.footstepstrail.status.steps",
                                        String.valueOf(config.getMaxSteps())).withStyle(ChatFormatting.AQUA));
                }

                sendChat(source, Component.translatable("text.footstepstrail.status.color",
                                (int) (config.getColorR() * 255),
                                (int) (config.getColorG() * 255),
                                (int) (config.getColorB() * 255)).withStyle(ChatFormatting.AQUA));

                sendChat(source, Component.translatable("text.footstepstrail.status.size", config.getMarkerSize())
                                .withStyle(ChatFormatting.AQUA));

                sendChat(source, Component.translatable("text.footstepstrail.status.opacity", config.getMaxOpacity())
                                .withStyle(ChatFormatting.AQUA));

                sendChat(source, Component.translatable("text.footstepstrail.status.rotation", config.isEnableRotation())
                                .withStyle(ChatFormatting.AQUA));

                sendChat(source, Component.translatable("text.footstepstrail.status.alternating", config.isEnableAlternatingFeet())
                                .withStyle(ChatFormatting.AQUA));

                sendChat(source, Component.translatable("text.footstepstrail.status.stealth", config.isStealthSneak())
                                .withStyle(ChatFormatting.AQUA));

                sendChat(source, Component.translatable("text.footstepstrail.status.scuffs", config.isHardSurfaceScuffs())
                                .withStyle(ChatFormatting.AQUA));

                sendChat(source, Component.translatable("text.footstepstrail.status.active", tracker.getCount())
                                .withStyle(ChatFormatting.YELLOW));

                return 1;
        }

        private static int executeRotation(CommandSourceStack source, boolean enabled) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setEnableRotation(enabled);
                config.save();
                sendChat(source, Component.translatable("text.footstepstrail.command.rotation",
                                Component.literal(String.valueOf(enabled)).withStyle(ChatFormatting.AQUA)));
                return 1;
        }

        private static int executeAlternating(CommandSourceStack source, boolean enabled) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setEnableAlternatingFeet(enabled);
                config.save();
                sendChat(source, Component.translatable("text.footstepstrail.command.alternating",
                                Component.literal(String.valueOf(enabled)).withStyle(ChatFormatting.AQUA)));
                return 1;
        }

        private static int executeStealth(CommandSourceStack source, boolean enabled) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setStealthSneak(enabled);
                config.save();
                sendChat(source, Component.translatable("text.footstepstrail.command.stealth",
                                Component.literal(String.valueOf(enabled)).withStyle(ChatFormatting.AQUA)));
                return 1;
        }

        private static int executeScuffs(CommandSourceStack source, boolean enabled) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setHardSurfaceScuffs(enabled);
                config.save();
                sendChat(source, Component.translatable("text.footstepstrail.command.scuffs",
                                Component.literal(String.valueOf(enabled)).withStyle(ChatFormatting.AQUA)));
                return 1;
        }
}
