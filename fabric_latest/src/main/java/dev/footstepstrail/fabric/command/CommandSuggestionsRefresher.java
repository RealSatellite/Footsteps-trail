package dev.footstepstrail.fabric.command;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;

/**
 * Rebuilds the chat's command suggestions after the footstep type changes.
 *
 * <p>Loaders copy client commands into the suggestion list only when the server sends its
 * command list, and they drop any command whose {@code requires} check fails at that moment. So
 * {@code /footsteps appearance}, which is only offered for classic footsteps, would stay shown or
 * hidden until the next world join. Replaying the last command list makes the loader run that
 * merge again with the current type. Fabric API does that for us here.
 */
public final class CommandSuggestionsRefresher {

    private CommandSuggestionsRefresher() {
    }

    /** Re-runs the loader's command merge so suggestions match the current settings. */
    public static void refresh() {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) {
            client.execute(CommandSuggestionsRefresher::refresh);
            return;
        }
        if (client.getConnection() == null) {
            return;
        }
        try {
            ClientCommands.refreshCommandCompletions();
        } catch (IllegalStateException ignored) {
            // No command list from the server yet; it will be merged when it arrives.
        }
    }
}
