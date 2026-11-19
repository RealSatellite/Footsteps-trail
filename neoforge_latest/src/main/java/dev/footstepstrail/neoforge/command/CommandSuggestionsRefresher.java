package dev.footstepstrail.neoforge.command;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;

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
    private static final Map<ClientPacketListener, ClientboundCommandsPacket> LAST_PACKETS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private CommandSuggestionsRefresher() {
    }

    /** Called by the mixin each time the server sends its command list. */
    public static void remember(ClientPacketListener listener, ClientboundCommandsPacket packet) {
        LAST_PACKETS.put(listener, packet);
    }

    /** Re-runs the loader's command merge so suggestions match the current settings. */
    public static void refresh() {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) {
            client.execute(CommandSuggestionsRefresher::refresh);
            return;
        }
        ClientPacketListener listener = client.getConnection();
        if (listener == null) {
            return;
        }
        ClientboundCommandsPacket packet = LAST_PACKETS.get(listener);
        if (packet == null) {
            return;
        }
        listener.handleCommands(packet);
    }
}
