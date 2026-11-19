package dev.footstepstrail.neoforge.mixin.command;

import dev.footstepstrail.neoforge.command.CommandSuggestionsRefresher;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the server's last command list so {@link CommandSuggestionsRefresher} can replay it. */
@Mixin(ClientPacketListener.class)
abstract class CommandsPacketMixin {
    @Inject(method = "handleCommands", at = @At("HEAD"))
    private void footsteps$rememberCommands(ClientboundCommandsPacket packet, CallbackInfo ci) {
        CommandSuggestionsRefresher.remember((ClientPacketListener) (Object) this, packet);
    }
}
