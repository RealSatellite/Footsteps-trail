package dev.footstepstrail.fabric.mixin.command;

import dev.footstepstrail.fabric.command.CommandSuggestionsRefresher;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.CommandTreeS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the server's last command list so {@link CommandSuggestionsRefresher} can replay it. */
@Mixin(ClientPlayNetworkHandler.class)
abstract class CommandsPacketMixin {
    @Inject(method = "onCommandTree", at = @At("HEAD"))
    private void footsteps$rememberCommands(CommandTreeS2CPacket packet, CallbackInfo ci) {
        CommandSuggestionsRefresher.remember((ClientPlayNetworkHandler) (Object) this, packet);
    }
}
