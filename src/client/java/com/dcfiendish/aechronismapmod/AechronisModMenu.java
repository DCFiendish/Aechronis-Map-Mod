package com.dcfiendish.aechronismapmod;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigManager;
import me.shedaniel.autoconfig.gui.ConfigScreenProvider;
import me.shedaniel.autoconfig.gui.registry.GuiRegistry;

public class AechronisModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // AutoConfig.getConfigScreen(Class, Screen) was removed from cloth-config's public
        // API — building the screen directly via ConfigScreenProvider (what that helper did
        // internally) is now the mod's own responsibility.
        return parent -> {
            @SuppressWarnings("unchecked")
            ConfigManager<AechronisConfig> manager =
                    (ConfigManager<AechronisConfig>) AutoConfig.getConfigHolder(AechronisConfig.class);
            return new ConfigScreenProvider<>(manager, new GuiRegistry(), parent).get();
        };
    }
}
