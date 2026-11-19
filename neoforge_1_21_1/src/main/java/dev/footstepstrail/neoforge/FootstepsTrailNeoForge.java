package dev.footstepstrail.neoforge;

import dev.footstepstrail.FootstepsTrail;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import dev.footstepstrail.screen.FootstepsSettingsScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * NeoForge entrypoint (client-only).
 */
// dist = CLIENT: NeoForge skips this class on dedicated servers, so a
// server pack that still contains the jar boots normally.
@Mod(value = FootstepsTrail.MOD_ID, dist = Dist.CLIENT)
public class FootstepsTrailNeoForge {

    public FootstepsTrailNeoForge(IEventBus modBus, ModContainer container) {
        FootstepsTrail.LOGGER.info("Initializing Footsteps Trail (NeoForge)");

        // This module is client-only; initialize client hooks directly.
        FootstepsTrailNeoForgeClient.init(modBus);

        // "Config" button in the NeoForge mods list
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (mod, parent) -> new FootstepsSettingsScreen(parent));
    }
}
