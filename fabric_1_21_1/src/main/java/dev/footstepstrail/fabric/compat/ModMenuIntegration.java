package dev.footstepstrail.fabric.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.footstepstrail.screen.FootstepsSettingsScreen;

/**
 * Settings button in Mod Menu. Only loaded when Mod Menu is installed.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return FootstepsSettingsScreen::new;
    }
}
