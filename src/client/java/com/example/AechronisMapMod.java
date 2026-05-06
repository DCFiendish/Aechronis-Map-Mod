package com.example;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class AechronisMapMod implements ClientModInitializer {

    public static AechronisMapData mapData;
    private static AechronisDataFetcher fetcher;
    private static boolean rendererEnabled = false;

    // Keybind to toggle War HUD (unbound by default)
    public static KeyMapping warHudToggle;

    @Override
    public void onInitializeClient() {
        System.out.println("[Aechronis] Initializing...");

        // Register config
        AutoConfig.register(AechronisConfig.class, GsonConfigSerializer::new);

        // Create data objects
        mapData = new AechronisMapData();
        fetcher = new AechronisDataFetcher();
        fetcher.mapData = mapData;

        // Register War HUD keybind (unbound)
        warHudToggle = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.aechronismapmod.war_hud_toggle",
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.aechronismapmod"
        ));

        // Register chat listener
        new AechronisChatListener(mapData).register();

        // Register War HUD renderer
        AechronisWarHud.register();

        // Handle keybind press
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (warHudToggle.consumeClick()) {
                AechronisWarHud.visible = !AechronisWarHud.visible;
            }
        });

        // Delay map renderer until world join
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!rendererEnabled) {
                AechronisRenderer renderer = new AechronisRenderer(mapData);
                xaeroplus.module.ModuleManager.addModule(renderer);
                renderer.enable();
                rendererEnabled = true;
                System.out.println("[Aechronis] Renderer enabled.");
            }
        });

        // Start data fetcher
        fetcher.start();

        System.out.println("[Aechronis] Initialized!");
    }
}
