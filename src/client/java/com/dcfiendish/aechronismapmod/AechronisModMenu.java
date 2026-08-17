package com.dcfiendish.aechronismapmod;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigManager;
import me.shedaniel.autoconfig.gui.ConfigScreenProvider;
import me.shedaniel.autoconfig.gui.DefaultGuiProviders;
import me.shedaniel.autoconfig.gui.DefaultGuiTransformers;
import me.shedaniel.autoconfig.gui.registry.GuiRegistry;

public class AechronisModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // AutoConfig.getConfigScreen(Class, Screen) was removed from cloth-config's public
        // API — building the screen directly via ConfigScreenProvider (what that helper did
        // internally) is now the mod's own responsibility.
        //
        // A bare `new GuiRegistry()` is EMPTY — no field-type-to-widget providers registered.
        // DefaultGuiRegistryAccess is NOT a source of default providers despite its name —
        // confirmed against cloth-config-26.2.155+fabric bytecode, its get() unconditionally
        // logs "No GUI provider registered for field ..." and returns an empty list; it's a
        // terminal fallback for reporting an unhandled field, not a registry of defaults. The
        // actual default providers (checkbox for boolean, slider/textfield for int/float,
        // dropdown for enum, etc.) are populated by DefaultGuiProviders.apply(GuiRegistry) /
        // DefaultGuiTransformers.apply(GuiRegistry), which is what the removed
        // AutoConfig.getConfigScreen() helper called internally.
        return parent -> {
            @SuppressWarnings("unchecked")
            ConfigManager<AechronisConfig> manager =
                    (ConfigManager<AechronisConfig>) AutoConfig.getConfigHolder(AechronisConfig.class);
            GuiRegistry registry = new GuiRegistry();
            DefaultGuiProviders.apply(registry);
            DefaultGuiTransformers.apply(registry);
            return new ConfigScreenProvider<>(manager, registry, parent).get();
        };
    }
}
