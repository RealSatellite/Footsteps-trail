package dev.footstepstrail.forge.command;

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
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Registers and handles the /footsteps client command on Forge.
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
                        "cross",
        };

        private static final String[] TYPE_OPTIONS = new String[] {
                        "terrain",
                        "surface",
                        "classic",
        };

        private FootstepsCommand() {
        }

        public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
                // /footsteps appearance is offered only for classic footsteps; keep suggestions in step.
                FootstepsConfig.setTypeChangeListener(CommandSuggestionsRefresher::refresh);
                dispatcher.register(
                                CommandManager.literal("footsteps")
                                                .executes(ctx -> executeStatus(ctx.getSource()))

                                                .then(CommandManager.literal("toggle")
                                                                .executes(ctx -> executeToggle(ctx.getSource())))

                                                // /footsteps type <terrain|surface|classic>
                                                .then(CommandManager.literal("type")
                                                                .then(CommandManager.argument("type",
                                                                                StringArgumentType.word())
                                                                                .suggests((context, builder) -> CommandSource
                                                                                                .suggestMatching(TYPE_OPTIONS, builder))
                                                                                .executes(ctx -> executeType(
                                                                                                ctx.getSource(),
                                                                                                StringArgumentType.getString(ctx, "type")))))

                                                .then(CommandManager.literal("mode")
                                                                .then(CommandManager
                                                                                .argument("mode",
                                                                                                StringArgumentType
                                                                                                                .word())
                                                                                .suggests((context,
                                                                                                builder) -> CommandSource
                                                                                                                .suggestMatching(
                                                                                                                                new String[] { "combined", "time",
                                                                                                                                                "steps" },
                                                                                                                                builder))
                                                                                .executes(ctx -> executeMode(
                                                                                                ctx.getSource(),
                                                                                                StringArgumentType
                                                                                                                .getString(ctx, "mode")))))

                                                .then(CommandManager.literal("config")
                                                                .then(CommandManager.literal("time")
                                                                                .then(CommandManager.literal("set")
                                                                                                .then(CommandManager
                                                                                                                .argument("seconds",
                                                                                                                                IntegerArgumentType
                                                                                                                                                .integer(1, 300))
                                                                                                                .executes(ctx -> executeDuration(
                                                                                                                                ctx.getSource(),
                                                                                                                                IntegerArgumentType
                                                                                                                                                .getInteger(ctx, "seconds"))))))
                                                                .then(CommandManager.literal("steps")
                                                                                .then(CommandManager.literal("set")
                                                                                                .then(CommandManager
                                                                                                                .argument("count",
                                                                                                                                IntegerArgumentType
                                                                                                                                                .integer(1, 500))
                                                                                                                .executes(ctx -> executeSteps(
                                                                                                                                ctx.getSource(),
                                                                                                                                IntegerArgumentType
                                                                                                                                                .getInteger(ctx, "count"))))))
                                                                .then(CommandManager.literal("rotation")
                                                                                .then(CommandManager
                                                                                                .argument("enabled",
                                                                                                                com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                                .bool())
                                                                                                .executes(ctx -> executeRotation(
                                                                                                                                ctx.getSource(),
                                                                                                                                com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                                                .getBool(ctx,
                                                                                                                                                                "enabled")))))
                                                                .then(CommandManager.literal("alternating")
                                                                                .then(CommandManager
                                                                                                .argument("enabled",
                                                                                                                com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                                .bool())
                                                                                                .executes(ctx -> executeAlternating(
                                                                                                                                ctx.getSource(),
                                                                                                                                com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                                                .getBool(ctx,
                                                                                                                                                                "enabled")))))
                                                                .then(CommandManager.literal("stealth")
                                                                                .then(CommandManager
                                                                                                .argument("enabled",
                                                                                                                com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                                .bool())
                                                                                                .executes(ctx -> executeStealth(
                                                                                                                ctx.getSource(),
                                                                                                                com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                                .getBool(ctx,
                                                                                                                                                "enabled")))))
                                                                .then(CommandManager.literal("scuffs")
                                                                                .then(CommandManager
                                                                                                .argument("enabled",
                                                                                                                com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                                .bool())
                                                                                                .executes(ctx -> executeScuffs(
                                                                                                                ctx.getSource(),
                                                                                                                com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                                .getBool(ctx,
                                                                                                                                                "enabled"))))))

                                                // /footsteps size <value>
                                                .then(CommandManager.literal("size")
                                                                .then(CommandManager
                                                                                .argument("value",
                                                                                                FloatArgumentType
                                                                                                                .floatArg(0.05f, 2.0f))
                                                                                .executes(ctx -> executeSize(
                                                                                                ctx.getSource(),
                                                                                                FloatArgumentType
                                                                                                                .getFloat(ctx, "value")))))

                                                // /footsteps appearance shape|color|glow  (only while type is classic)
                                                .then(CommandManager.literal("appearance")
                                                                .requires(src -> FootstepsConfig.getInstance().isClassic())
                                                                .then(CommandManager.literal("glow")
                                                                                .executes(ctx -> executeGlow(ctx.getSource(),
                                                                                                !FootstepsConfig.getInstance().isGlow()))
                                                                                .then(CommandManager
                                                                                                .argument("enabled",
                                                                                                                com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                                .bool())
                                                                                                .executes(ctx -> executeGlow(
                                                                                                                ctx.getSource(),
                                                                                                                com.mojang.brigadier.arguments.BoolArgumentType
                                                                                                                                .getBool(ctx,
                                                                                                                                                "enabled")))))
                                                                .then(CommandManager.literal("shape")
                                                                                .then(CommandManager.argument("shape",
                                                                                                StringArgumentType
                                                                                                                .word())
                                                                                                .suggests((context,
                                                                                                                builder) -> CommandSource
                                                                                                                                .suggestMatching(
                                                                                                                                                SHAPE_OPTIONS,
                                                                                                                                                builder))
                                                                                                .executes(ctx -> executeShape(
                                                                                                                ctx.getSource(),
                                                                                                                StringArgumentType
                                                                                                                                .getString(ctx, "shape")))))
                                                                .then(CommandManager.literal("color")
                                                                                .then(CommandManager
                                                                                                .argument("color",
                                                                                                                StringArgumentType
                                                                                                                .word())
                                                                                                .suggests((context,
                                                                                                                builder) -> CommandSource
                                                                                                                                .suggestMatching(
                                                                                                                                                COLOR_OPTIONS,
                                                                                                                                                builder))
                                                                                                .executes(ctx -> executeColor(
                                                                                                                ctx.getSource(),
                                                                                                                StringArgumentType
                                                                                                                                .getString(ctx, "color"))))))

                                                // /footsteps help
                                                .then(CommandManager.literal("help")
                                                                .executes(ctx -> executeHelp(ctx.getSource())))

                                                .then(CommandManager.literal("clear")
                                                                .executes(ctx -> executeClear(ctx.getSource())))

                                                .then(CommandManager.literal("status")
                                                                .executes(ctx -> executeStatus(ctx.getSource()))));
        }

        private static void sendChat(ServerCommandSource source, Text message) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player != null) {
                        mc.player.sendMessage(message, false);
                } else {
                        // Best-effort fallback; client commands should always have a player.
                        source.sendFeedback(() -> message, false);
                }
        }

        private static int executeToggle(ServerCommandSource source) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.toggle();
                config.save();

                String status = config.isEnabled() ? "enabled" : "disabled";
                Formatting color = config.isEnabled() ? Formatting.GREEN : Formatting.RED;

                sendChat(source, Text.translatable("text.footstepstrail.command.toggle",
                                Text.literal(status).formatted(color)).formatted(Formatting.GREEN));

                if (!config.isEnabled()) {
                        FootstepsTracker.getInstance().clear();
                }

                return 1;
        }

        private static int executeMode(ServerCommandSource source, String modeStr) {
                FootstepsConfig config = FootstepsConfig.getInstance();

                TrackingMode mode;
                try {
                        mode = TrackingMode.valueOf(modeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                        sendChat(source, Text.translatable("text.footstepstrail.command.invalid_mode")
                                        .formatted(Formatting.RED));
                        return 0;
                }

                config.setTrackingMode(mode);
                config.save();
                FootstepsTracker.getInstance().clear();

                sendChat(source, Text.translatable("text.footstepstrail.command.mode",
                                Text.literal(mode.name().toLowerCase()).formatted(Formatting.AQUA)));

                return 1;
        }

        private static int executeDuration(ServerCommandSource source, int seconds) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setMaxDurationSeconds(seconds);
                config.save();

                sendChat(source, Text.translatable("text.footstepstrail.command.duration", seconds)
                                .formatted(Formatting.GREEN));

                return 1;
        }

        private static int executeSteps(ServerCommandSource source, int count) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setMaxSteps(count);
                config.save();

                sendChat(source, Text.translatable("text.footstepstrail.command.steps", count)
                                .formatted(Formatting.GREEN));

                return 1;
        }

        private static int executeColor(ServerCommandSource source, String colorName) {
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
                                sendChat(source, Text.translatable("text.footstepstrail.command.invalid_color")
                                                .formatted(Formatting.RED));
                                return 0;
                }

                config.save();
                sendChat(source, Text.translatable("text.footstepstrail.command.color",
                                Text.literal(normalized).formatted(Formatting.AQUA))
                                .formatted(Formatting.GREEN));

                return 1;
        }

        private static int executeSize(ServerCommandSource source, float value) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setMarkerSize(value);
                config.save();

                sendChat(source, Text.translatable("text.footstepstrail.command.size", value)
                                .formatted(Formatting.GREEN));

                return 1;
        }

        private static int executeType(ServerCommandSource source, String typeStr) {
                FootstepType type;
                try {
                        type = FootstepType.valueOf(typeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                        sendChat(source, Text.translatable("text.footstepstrail.command.invalid_type")
                                        .formatted(Formatting.RED));
                        return 0;
                }

                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setType(type);
                config.save();
                FootstepsTracker.getInstance().clear();

                sendChat(source, Text.translatable("text.footstepstrail.command.type",
                                Text.literal(type.name().toLowerCase()).formatted(Formatting.AQUA))
                                .formatted(Formatting.GREEN));
                return 1;
        }

        private static int executeGlow(ServerCommandSource source, boolean enabled) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setGlow(enabled);
                config.save();

                sendChat(source, Text.translatable("text.footstepstrail.command.glow",
                                Text.literal(enabled ? "enabled" : "disabled")
                                                .formatted(enabled ? Formatting.GREEN : Formatting.RED))
                                .formatted(Formatting.GREEN));
                return 1;
        }

        private static int executeShape(ServerCommandSource source, String shapeStr) {
                FootstepsConfig config = FootstepsConfig.getInstance();

                FootstepShape shape;
                try {
                        shape = FootstepShape.valueOf(shapeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                        sendChat(source, Text.translatable("text.footstepstrail.command.invalid_shape")
                                        .formatted(Formatting.RED));
                        return 0;
                }

                config.setShape(shape);
                config.save();

                sendChat(source, Text.translatable("text.footstepstrail.command.shape",
                                Text.literal(shape.name().toLowerCase()).formatted(Formatting.AQUA))
                                .formatted(Formatting.GREEN));
                return 1;
        }

        private static int executeClear(ServerCommandSource source) {
                FootstepsTracker.getInstance().clear();
                sendChat(source, Text.translatable("text.footstepstrail.command.clear")
                                .formatted(Formatting.YELLOW));
                return 1;
        }

        private static int executeHelp(ServerCommandSource source) {
                sendChat(source, Text.translatable("text.footstepstrail.help.title").formatted(Formatting.GOLD));
                for (String[] entry : FootstepsHelp.ENTRIES) {
                    sendChat(source, Text.literal(FootstepsHelp.command(entry)).formatted(Formatting.AQUA)
                            .append(Text.literal(FootstepsHelp.description(entry)).formatted(Formatting.GRAY)));
                }
                return 1;
        }

        private static int executeStatus(ServerCommandSource source) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                FootstepsTracker tracker = FootstepsTracker.getInstance();

                String enabledStr = config.isEnabled() ? "Enabled" : "Disabled";
                Formatting enabledColor = config.isEnabled() ? Formatting.GREEN : Formatting.RED;

                sendChat(source, Text.translatable("text.footstepstrail.status.title")
                                .formatted(Formatting.GOLD));

                sendChat(source, Text.translatable("text.footstepstrail.status.status",
                                Text.literal(enabledStr).formatted(enabledColor)));

                sendChat(source, Text.translatable("text.footstepstrail.status.type",
                                Text.literal(config.getType().name().toLowerCase()).formatted(Formatting.AQUA)));
                if (config.isTerrainUnsupported()) {
                        sendChat(source, Text.translatable("text.footstepstrail.status.terrain_fallback",
                                        dev.footstepstrail.deformation.TerrainSupport.unsupportedRenderer()).formatted(Formatting.YELLOW));
                }

                sendChat(source, Text.translatable("text.footstepstrail.status.mode",
                                Text.literal(config.getTrackingMode().name().toLowerCase())
                                                .formatted(Formatting.AQUA)));

                if (config.isClassic()) {
                        sendChat(source, Text.translatable("text.footstepstrail.status.shape",
                                        Text.literal(config.getShape().name().toLowerCase()).formatted(Formatting.AQUA)));
                        sendChat(source, Text.translatable("text.footstepstrail.status.glow",
                                        Text.literal(config.isGlow() ? "Enabled" : "Disabled")
                                                        .formatted(config.isGlow() ? Formatting.GREEN : Formatting.RED)));
                }

                if (config.getTrackingMode().usesTime()) {
                        sendChat(source, Text.translatable("text.footstepstrail.status.duration",
                                        String.valueOf(config.getMaxDurationSeconds())).formatted(Formatting.AQUA));
                }
                if (config.getTrackingMode().usesSteps()) {
                        sendChat(source, Text.translatable("text.footstepstrail.status.steps",
                                        String.valueOf(config.getMaxSteps())).formatted(Formatting.AQUA));
                }

                sendChat(source, Text.translatable("text.footstepstrail.status.color",
                                (int) (config.getColorR() * 255),
                                (int) (config.getColorG() * 255),
                                (int) (config.getColorB() * 255)).formatted(Formatting.AQUA));

                sendChat(source, Text.translatable("text.footstepstrail.status.size", config.getMarkerSize())
                                .formatted(Formatting.AQUA));

                sendChat(source, Text.translatable("text.footstepstrail.status.opacity", config.getMaxOpacity())
                                .formatted(Formatting.AQUA));

                sendChat(source, Text.translatable("text.footstepstrail.status.rotation", config.isEnableRotation())
                                .formatted(Formatting.AQUA));

                sendChat(source, Text.translatable("text.footstepstrail.status.alternating", config.isEnableAlternatingFeet())
                                .formatted(Formatting.AQUA));

                sendChat(source, Text.translatable("text.footstepstrail.status.stealth", config.isStealthSneak())
                                .formatted(Formatting.AQUA));

                sendChat(source, Text.translatable("text.footstepstrail.status.scuffs", config.isHardSurfaceScuffs())
                                .formatted(Formatting.AQUA));

                sendChat(source, Text.translatable("text.footstepstrail.status.active", tracker.getCount())
                                .formatted(Formatting.YELLOW));

                return 1;
        }

        private static int executeRotation(ServerCommandSource source, boolean enabled) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setEnableRotation(enabled);
                config.save();
                sendChat(source, Text.translatable("text.footstepstrail.command.rotation",
                                Text.literal(String.valueOf(enabled)).formatted(Formatting.AQUA)));
                return 1;
        }

        private static int executeAlternating(ServerCommandSource source, boolean enabled) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setEnableAlternatingFeet(enabled);
                config.save();
                sendChat(source, Text.translatable("text.footstepstrail.command.alternating",
                                Text.literal(String.valueOf(enabled)).formatted(Formatting.AQUA)));
                return 1;
        }

        private static int executeStealth(ServerCommandSource source, boolean enabled) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setStealthSneak(enabled);
                config.save();
                sendChat(source, Text.translatable("text.footstepstrail.command.stealth",
                                Text.literal(String.valueOf(enabled)).formatted(Formatting.AQUA)));
                return 1;
        }

        private static int executeScuffs(ServerCommandSource source, boolean enabled) {
                FootstepsConfig config = FootstepsConfig.getInstance();
                config.setHardSurfaceScuffs(enabled);
                config.save();
                sendChat(source, Text.translatable("text.footstepstrail.command.scuffs",
                                Text.literal(String.valueOf(enabled)).formatted(Formatting.AQUA)));
                return 1;
        }
}
