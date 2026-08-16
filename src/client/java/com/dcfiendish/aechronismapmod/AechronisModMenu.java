package com.dcfiendish.aechronismapmod;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigManager;
import me.shedaniel.autoconfig.gui.ConfigScreenProvider;
import me.shedaniel.autoconfig.gui.registry.ComposedGuiRegistryAccess;
import me.shedaniel.autoconfig.gui.registry.DefaultGuiRegistryAccess;
import me.shedaniel.autoconfig.gui.registry.GuiRegistry;

public class AechronisModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // AutoConfig.getConfigScreen(Class, Screen) was removed from cloth-config's public
        // API — building the screen directly via ConfigScreenProvider (what that helper did
        // internally) is now the mod's own responsibility.
        //
        // A bare `new GuiRegistry()` is EMPTY — no field-type-to-widget providers registered
        // (confirmed against cloth-config-26.2.155+fabric bytecode: its constructor only
        // allocates empty maps). Without DefaultGuiRegistryAccess composed in, every field in
        // AechronisConfig (booleans, ints, floats) has no provider to turn it into a widget,
        // so the screen renders with no controls. The removed AutoConfig.getConfigScreen()
        // always composed a DefaultGuiRegistryAccess in — this mirrors that.
        return parent -> {
            @SuppressWarnings("unchecked")
            ConfigManager<AechronisConfig> manager =
                    (ConfigManager<AechronisConfig>) AutoConfig.getConfigHolder(AechronisConfig.class);
            var registry = new ComposedGuiRegistryAccess(new GuiRegistry(), new DefaultGuiRegistryAccess());
            return new ConfigScreenProvider<>(manager, registry, parent).get();
        };
    }
}
