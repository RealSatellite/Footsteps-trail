package dev.footstepstrail.forge;

import dev.footstepstrail.FootstepsTrail;
import dev.footstepstrail.Tags;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Forge 1.12.2 entrypoint (client-only behavior).
 */
@Mod(
        modid = FootstepsTrail.MOD_ID,
        name = "Footsteps Trail",
        version = Tags.TAG_VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        clientSideOnly = true,
        guiFactory = "dev.footstepstrail.forge.FootstepsGuiFactory"
)
public class FootstepsTrailForge {

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Common init is handled in the client initializer.
        if (event.getSide().isClient()) {
            FootstepsTrailForgeClient.preInit();
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide().isClient()) {
            FootstepsTrailForgeClient.init();
        }
    }
}
