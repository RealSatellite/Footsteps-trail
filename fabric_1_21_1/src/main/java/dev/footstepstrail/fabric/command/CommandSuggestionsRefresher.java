package dev.footstepstrail.fabric.command;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.CommandTreeS2CPacket;

/**
 * Rebuilds the chat's command suggestions after the footstep type changes.
 *
 * <p>Loaders copy client commands into the suggestion list only when the server sends its
 * command list, and they drop any command whose {@code requires} check fails at that moment. So
 * {@code /footsteps appearance}, which is only offered for classic footsteps, would stay shown or
 * hidden until the next world join. Replaying the last command list makes the loader run that
 * merge again with the current type.
 */
public final class CommandSuggestionsRefresher {

    /**
     * The last command list per connection. Keys are weak, so a list is released together with
     * its connection once the player disconnects; a refresh never replays another server's list.
     */
    private static final Map<ClientPlayNetworkHandler, CommandTreeS2CPacket> LAST_PACKETS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private CommandSuggestionsRefresher() {
    }

    /** Called by the mixin each time the server sends its command list. */
    public static void remember(ClientPlayNetworkHandler listener, CommandTreeS2CPacket packet) {
        LAST_PACKETS.put(listener, packet);
    }

    /** Re-runs the loader's command merge so suggestions match the current settings. */
    public static void refresh() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!client.isOnThread()) {
            client.execute(CommandSuggestionsRefresher::refresh);
            return;
        }
        ClientPlayNetworkHandler listener = client.getNetworkHandler();
        if (listener == null) {
            return;
        }
        CommandTreeS2CPacket packet = LAST_PACKETS.get(listener);
        if (packet == null) {
            return;
        }
        listener.onCommandTree(packet);
    }
}
