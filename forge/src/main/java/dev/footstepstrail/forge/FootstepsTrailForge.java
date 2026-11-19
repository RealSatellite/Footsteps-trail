package dev.footstepstrail.forge;

import dev.footstepstrail.FootstepsTrail;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge entrypoint (client-only).
 */
@Mod(FootstepsTrail.MOD_ID)
public class FootstepsTrailForge {

    public FootstepsTrailForge(FMLJavaModLoadingContext context) {
        FootstepsTrail.LOGGER.info("Initializing Footsteps Trail (Forge)");

        // Client-only: all gameplay hooks are registered from the client initializer.
        // The client class is only loaded inside this branch, so dedicated
        // servers never touch client-only code.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            FootstepsTrailForgeClient.init(context);
        }
    }
}
