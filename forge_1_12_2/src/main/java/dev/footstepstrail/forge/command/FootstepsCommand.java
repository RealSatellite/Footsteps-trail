package dev.footstepstrail.forge.command;

import dev.footstepstrail.command.FootstepsHelp;
import dev.footstepstrail.config.FootstepShape;
import dev.footstepstrail.config.FootstepType;
import dev.footstepstrail.config.FootstepsConfig;
import dev.footstepstrail.config.TrackingMode;
import dev.footstepstrail.tracking.FootstepsTracker;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

/**
 * Client command: /footsteps
 */
public class FootstepsCommand extends CommandBase {

    @Override
    public String getName() {
        return "footsteps";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return getStaticUsage();
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            executeStatus(sender);
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "toggle":
                executeToggle(sender);
                return;

            case "type":
                if (args.length < 2) {
                    send(sender, tr("text.footstepstrail.command.invalid_type").setStyle(style(TextFormatting.RED)),
                            false);
                    return;
                }
                executeType(sender, args[1]);
                return;

            case "mode":
                if (args.length < 2) {
                    send(sender, tr("text.footstepstrail.command.invalid_mode").setStyle(style(TextFormatting.RED)),
                            false);
                    return;
                }
                executeMode(sender, args[1]);
                return;

            case "size":
                if (args.length < 2) {
                    send(sender, new TextComponentString(getStaticUsage()).setStyle(style(TextFormatting.RED)), false);
                    return;
                }
                try {
                    executeSize(sender, Float.parseFloat(args[1]));
                } catch (NumberFormatException e) {
                    send(sender, new TextComponentString("Invalid number").setStyle(style(TextFormatting.RED)), false);
                }
                return;

            case "config":
                executeConfig(sender, args);
                return;

            case "appearance":
                executeAppearance(sender, args);
                return;

            case "help":
                executeHelp(sender);
                return;

            case "clear":
                executeClear(sender);
                return;

            default:
                send(sender, new TextComponentString(getUsage(sender)).setStyle(style(TextFormatting.RED)), false);
        }
    }

    /**
     * Tab completion, worked out on every key press, so {@code appearance} is offered only while
     * the type is classic, matching the other loaders.
     */
    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
            @Nullable BlockPos targetPos) {
        boolean classic = FootstepsConfig.getInstance().isClassic();
        if (args.length == 1) {
            return classic
                    ? getListOfStringsMatchingLastWord(args, "appearance", "clear", "config", "help", "mode", "size",
                            "status", "toggle", "type")
                    : getListOfStringsMatchingLastWord(args, "clear", "config", "help", "mode", "size", "status",
                            "toggle", "type");
        }
        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            switch (sub) {
                case "type":
                    return getListOfStringsMatchingLastWord(args, "terrain", "surface", "classic");
                case "mode":
                    return getListOfStringsMatchingLastWord(args, "combined", "time", "steps");
                case "config":
                    return getListOfStringsMatchingLastWord(args, "alternating", "rotation", "scuffs", "stealth",
                            "steps", "time");
                case "appearance":
                    return classic ? getListOfStringsMatchingLastWord(args, "color", "glow", "shape")
                            : Collections.<String>emptyList();
                default:
                    return Collections.emptyList();
            }
        }
        if (args.length == 3) {
            String which = args[1].toLowerCase();
            if ("config".equals(sub)) {
                if ("time".equals(which) || "steps".equals(which)) {
                    return getListOfStringsMatchingLastWord(args, "set");
                }
                return getListOfStringsMatchingLastWord(args, "true", "false");
            }
            if ("appearance".equals(sub) && classic) {
                if ("shape".equals(which)) {
                    return getListOfStringsMatchingLastWord(args, "square", "circle", "paw", "cross");
                }
                if ("color".equals(which)) {
                    return getListOfStringsMatchingLastWord(args, "white", "gray", "red", "green", "blue", "yellow");
                }
                if ("glow".equals(which)) {
                    return getListOfStringsMatchingLastWord(args, "true", "false");
                }
            }
        }
        return Collections.emptyList();
    }

    private static void executeToggle(ICommandSender sender) {
        FootstepsConfig config = FootstepsConfig.getInstance();
        config.toggle();
        config.save();

        String status = config.isEnabled() ? "enabled" : "disabled";
        TextFormatting color = config.isEnabled() ? TextFormatting.GREEN : TextFormatting.RED;

        send(sender,
                tr("text.footstepstrail.command.toggle", new TextComponentString(status).setStyle(style(color)))
                        .setStyle(style(TextFormatting.GREEN)),
                false);

        if (!config.isEnabled()) {
            FootstepsTracker.getInstance().clear();
        }
    }

    private static void executeMode(ICommandSender sender, String modeStr) {
        FootstepsConfig config = FootstepsConfig.getInstance();

        TrackingMode mode;
        try {
            mode = TrackingMode.valueOf(modeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            send(sender, tr("text.footstepstrail.command.invalid_mode").setStyle(style(TextFormatting.RED)), false);
            return;
        }

        config.setTrackingMode(mode);
        config.save();
        FootstepsTracker.getInstance().clear();

        send(sender,
                tr("text.footstepstrail.command.mode",
                        new TextComponentString(mode.name().toLowerCase()).setStyle(style(TextFormatting.AQUA))),
                false);
    }

    private static void executeConfig(ICommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, new TextComponentString(getStaticUsage()).setStyle(style(TextFormatting.RED)), false);
            return;
        }

        String which = args[1].toLowerCase();

        if ("rotation".equals(which)) {
            boolean val = Boolean.parseBoolean(args[2]);
            executeRotation(sender, val);
            return;
        } else if ("alternating".equals(which)) {
            boolean val = Boolean.parseBoolean(args[2]);
            executeAlternating(sender, val);
            return;
        } else if ("stealth".equals(which)) {
            boolean val = Boolean.parseBoolean(args[2]);
            executeStealth(sender, val);
            return;
        } else if ("scuffs".equals(which)) {
            boolean val = Boolean.parseBoolean(args[2]);
            executeScuffs(sender, val);
            return;
        }

        // /footsteps config time set <seconds>
        // /footsteps config steps set <count>
        if (args.length < 4 || !"set".equalsIgnoreCase(args[2])) {
            send(sender, new TextComponentString(getStaticUsage()).setStyle(style(TextFormatting.RED)), false);
            return;
        }

        String valueStr = args[3];

        try {
            int value = Integer.parseInt(valueStr);
            if ("time".equals(which)) {
                executeDuration(sender, value);
            } else if ("steps".equals(which)) {
                executeSteps(sender, value);
            } else {
                send(sender, new TextComponentString(getStaticUsage()).setStyle(style(TextFormatting.RED)), false);
            }
        } catch (NumberFormatException e) {
            send(sender, new TextComponentString("Invalid number").setStyle(style(TextFormatting.RED)), false);
        }
    }

    private static void executeAppearance(ICommandSender sender, String[] args) {
        // /footsteps appearance shape <name>
        // /footsteps appearance color <name>
        // /footsteps appearance glow [true|false]
        // Only available while the footstep type is classic.
        if (!FootstepsConfig.getInstance().isClassic()) {
            send(sender, tr("text.footstepstrail.command.classic_only").setStyle(style(TextFormatting.RED)), false);
            return;
        }
        if (args.length < 2) {
            send(sender, new TextComponentString(getStaticUsage()).setStyle(style(TextFormatting.RED)), false);
            return;
        }

        String which = args[1].toLowerCase();
        switch (which) {
            case "glow":
                executeGlow(sender, args.length < 3
                        ? !FootstepsConfig.getInstance().isGlow()
                        : Boolean.parseBoolean(args[2]));
                return;
            case "shape":
                if (args.length < 3) {
                    send(sender, new TextComponentString(getStaticUsage()).setStyle(style(TextFormatting.RED)), false);
                    return;
                }
                executeShape(sender, args[2]);
                return;
            case "color":
                if (args.length < 3) {
                    send(sender, new TextComponentString(getStaticUsage()).setStyle(style(TextFormatting.RED)), false);
                    return;
                }
                executeColor(sender, args[2]);
                return;
            default:
                send(sender, new TextComponentString(getStaticUsage()).setStyle(style(TextFormatting.RED)), false);
        }
    }

    private static void executeDuration(ICommandSender sender, int seconds) {
        FootstepsConfig config = FootstepsConfig.getInstance();
        config.setMaxDurationSeconds(seconds);
        config.save();

        send(sender, tr("text.footstepstrail.command.duration", seconds).setStyle(style(TextFormatting.GREEN)), false);
    }

    private static void executeSteps(ICommandSender sender, int count) {
        FootstepsConfig config = FootstepsConfig.getInstance();
        config.setMaxSteps(count);
        config.save();

        send(sender, tr("text.footstepstrail.command.steps", count).setStyle(style(TextFormatting.GREEN)), false);
    }

    private static void executeColor(ICommandSender sender, String colorName) {
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
                send(sender, tr("text.footstepstrail.command.invalid_color").setStyle(style(TextFormatting.RED)),
                        false);
                return;
        }

        config.save();
        send(sender,
                tr("text.footstepstrail.command.color",
                        new TextComponentString(normalized).setStyle(style(TextFormatting.AQUA)))
                        .setStyle(style(TextFormatting.GREEN)),
                false);
    }

    private static void executeSize(ICommandSender sender, float value) {
        FootstepsConfig config = FootstepsConfig.getInstance();
        config.setMarkerSize(value);
        config.save();

        send(sender, tr("text.footstepstrail.command.size", value).setStyle(style(TextFormatting.GREEN)), false);
    }

    private static void executeClear(ICommandSender sender) {
        FootstepsTracker.getInstance().clear();
        send(sender, tr("text.footstepstrail.command.clear").setStyle(style(TextFormatting.YELLOW)), false);
    }

    private static void executeType(ICommandSender sender, String typeStr) {
        FootstepType type;
        try {
            type = FootstepType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            send(sender, tr("text.footstepstrail.command.invalid_type").setStyle(style(TextFormatting.RED)), false);
            return;
        }

        FootstepsConfig config = FootstepsConfig.getInstance();
        config.setType(type);
        config.save();
        FootstepsTracker.getInstance().clear();

        send(sender, tr("text.footstepstrail.command.type",
                new TextComponentString(type.name().toLowerCase()).setStyle(style(TextFormatting.AQUA)))
                .setStyle(style(TextFormatting.GREEN)), false);
    }

    private static void executeGlow(ICommandSender sender, boolean enabled) {
        FootstepsConfig config = FootstepsConfig.getInstance();
        config.setGlow(enabled);
        config.save();

        send(sender, tr("text.footstepstrail.command.glow",
                new TextComponentString(enabled ? "enabled" : "disabled")
                        .setStyle(style(enabled ? TextFormatting.GREEN : TextFormatting.RED)))
                .setStyle(style(TextFormatting.GREEN)), false);
    }

    private static void executeShape(ICommandSender sender, String shapeStr) {
        FootstepsConfig config = FootstepsConfig.getInstance();

        FootstepShape shape;
        try {
            shape = FootstepShape.valueOf(shapeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            send(sender, tr("text.footstepstrail.command.invalid_shape").setStyle(style(TextFormatting.RED)), false);
            return;
        }

        config.setShape(shape);
        config.save();

        send(sender, tr("text.footstepstrail.command.shape",
                new TextComponentString(shape.name().toLowerCase()).setStyle(style(TextFormatting.AQUA)))
                .setStyle(style(TextFormatting.GREEN)), false);
    }

    private static void executeHelp(ICommandSender sender) {
        send(sender, tr("text.footstepstrail.help.title").setStyle(style(TextFormatting.GOLD)), false);
        for (String[] entry : FootstepsHelp.ENTRIES) {
            send(sender, new TextComponentString(FootstepsHelp.command(entry)).setStyle(style(TextFormatting.AQUA))
                    .appendSibling(new TextComponentString(FootstepsHelp.description(entry))
                            .setStyle(style(TextFormatting.GRAY))), false);
        }
    }

    private static void executeStatus(ICommandSender sender) {
        FootstepsConfig config = FootstepsConfig.getInstance();
        FootstepsTracker tracker = FootstepsTracker.getInstance();

        String enabledStr = config.isEnabled() ? "Enabled" : "Disabled";
        TextFormatting enabledColor = config.isEnabled() ? TextFormatting.GREEN : TextFormatting.RED;

        send(sender, tr("text.footstepstrail.status.title").setStyle(style(TextFormatting.GOLD)), false);
        send(sender, tr("text.footstepstrail.status.status",
                new TextComponentString(enabledStr).setStyle(style(enabledColor))), false);
        send(sender, tr("text.footstepstrail.status.type",
                new TextComponentString(config.getType().name().toLowerCase()).setStyle(style(TextFormatting.AQUA))),
                false);
        if (config.isTerrainUnsupported()) {
            send(sender, tr("text.footstepstrail.status.terrain_fallback", dev.footstepstrail.deformation.TerrainSupport.unsupportedRenderer())
                    .setStyle(style(TextFormatting.YELLOW)), false);
        }
        send(sender,
                tr("text.footstepstrail.status.mode",
                        new TextComponentString(config.getTrackingMode().name().toLowerCase())
                                .setStyle(style(TextFormatting.AQUA))),
                false);
        if (config.isClassic()) {
            send(sender, tr("text.footstepstrail.status.shape",
                    new TextComponentString(config.getShape().name().toLowerCase()).setStyle(style(TextFormatting.AQUA))),
                    false);
            send(sender, tr("text.footstepstrail.status.glow",
                    new TextComponentString(config.isGlow() ? "Enabled" : "Disabled")
                            .setStyle(style(config.isGlow() ? TextFormatting.GREEN : TextFormatting.RED))), false);
        }

        if (config.getTrackingMode().usesTime()) {
            send(sender, tr("text.footstepstrail.status.duration", String.valueOf(config.getMaxDurationSeconds()))
                    .setStyle(style(TextFormatting.AQUA)), false);
        }
        if (config.getTrackingMode().usesSteps()) {
            send(sender, tr("text.footstepstrail.status.steps", String.valueOf(config.getMaxSteps()))
                    .setStyle(style(TextFormatting.AQUA)), false);
        }

        send(sender, tr("text.footstepstrail.status.color",
                (int) (config.getColorR() * 255),
                (int) (config.getColorG() * 255),
                (int) (config.getColorB() * 255)).setStyle(style(TextFormatting.AQUA)), false);

        send(sender, tr("text.footstepstrail.status.size", config.getMarkerSize()).setStyle(style(TextFormatting.AQUA)),
                false);
        send(sender, tr("text.footstepstrail.status.opacity", config.getMaxOpacity()).setStyle(style(TextFormatting.AQUA)),
                false);
        send(sender, tr("text.footstepstrail.status.rotation", String.valueOf(config.isEnableRotation())).setStyle(style(TextFormatting.AQUA)),
                false);
        send(sender, tr("text.footstepstrail.status.alternating", String.valueOf(config.isEnableAlternatingFeet())).setStyle(style(TextFormatting.AQUA)),
                false);
        send(sender, tr("text.footstepstrail.status.stealth", String.valueOf(config.isStealthSneak())).setStyle(style(TextFormatting.AQUA)),
                false);
        send(sender, tr("text.footstepstrail.status.scuffs", String.valueOf(config.isHardSurfaceScuffs())).setStyle(style(TextFormatting.AQUA)),
                false);
        send(sender, tr("text.footstepstrail.status.active", tracker.getCount()).setStyle(style(TextFormatting.YELLOW)),
                false);
    }

    private static void executeRotation(ICommandSender sender, boolean enabled) {
        FootstepsConfig config = FootstepsConfig.getInstance();
        config.setEnableRotation(enabled);
        config.save();

        send(sender, tr("text.footstepstrail.command.rotation", String.valueOf(enabled)).setStyle(style(TextFormatting.GREEN)), false);
    }

    private static void executeAlternating(ICommandSender sender, boolean enabled) {
        FootstepsConfig config = FootstepsConfig.getInstance();
        config.setEnableAlternatingFeet(enabled);
        config.save();

        send(sender, tr("text.footstepstrail.command.alternating", String.valueOf(enabled)).setStyle(style(TextFormatting.GREEN)), false);
    }

    private static void executeStealth(ICommandSender sender, boolean enabled) {
        FootstepsConfig config = FootstepsConfig.getInstance();
        config.setStealthSneak(enabled);
        config.save();

        send(sender, tr("text.footstepstrail.command.stealth", String.valueOf(enabled)).setStyle(style(TextFormatting.GREEN)), false);
    }

    private static void executeScuffs(ICommandSender sender, boolean enabled) {
        FootstepsConfig config = FootstepsConfig.getInstance();
        config.setHardSurfaceScuffs(enabled);
        config.save();

        send(sender, tr("text.footstepstrail.command.scuffs", String.valueOf(enabled)).setStyle(style(TextFormatting.GREEN)), false);
    }

    private static void send(ICommandSender sender, ITextComponent component, boolean actionBar) {
        // Client command sender should be the client player.
        if (actionBar && Minecraft.getMinecraft().player != null) {
            Minecraft.getMinecraft().player.sendStatusMessage(component, true);
        } else {
            sender.sendMessage(component);
        }
    }

    private static ITextComponent tr(String key, Object... args) {
        return new TextComponentTranslation(key, args);
    }

    private static Style style(TextFormatting fmt) {
        return new Style().setColor(fmt);
    }

    private static String getStaticUsage() {
        return "/footsteps [toggle|type <terrain|surface|classic>|mode <combined|time|steps>|size <value>|config (time|steps) set <value>|config (rotation|alternating|stealth|scuffs) <true|false>|appearance (shape|color|glow) <value> (classic only)|clear|status|help]";
    }
}
