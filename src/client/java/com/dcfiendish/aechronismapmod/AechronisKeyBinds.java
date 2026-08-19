package com.dcfiendish.aechronismapmod;

import com.mojang.blaze3d.platform.InputConstants;
import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * One toggle keybind per map text-label category (see AechronisConfig). All registered
 * unbound (InputConstants.UNKNOWN) — no default keys chosen yet, user assigns via the
 * vanilla Controls menu (or a future config-driven default list).
 */
public class AechronisKeyBinds {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("aechronismapmod", "general"));

    public static final KeyMapping TOGGLE_NODE_LABELS =
            register("key.aechronismapmod.toggle_node_labels");
    public static final KeyMapping TOGGLE_TOWN_LABELS =
            register("key.aechronismapmod.toggle_town_labels");
    public static final KeyMapping TOGGLE_NATION_LABELS =
            register("key.aechronismapmod.toggle_nation_labels");
    public static final KeyMapping TOGGLE_BUILDING_LABELS =
            register("key.aechronismapmod.toggle_building_labels");
    public static final KeyMapping TOGGLE_TRAIN_STATION_LABELS =
            register("key.aechronismapmod.toggle_train_station_labels");

    private static KeyMapping register(String translationKey) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                translationKey,
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                CATEGORY
        ));
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            AechronisConfig cfg = AechronisConfig.get();
            boolean changed = false;

            while (TOGGLE_NODE_LABELS.consumeClick()) {
                cfg.showNodeLabels = !cfg.showNodeLabels;
                changed = true;
            }
            while (TOGGLE_TOWN_LABELS.consumeClick()) {
                cfg.showTownLabels = !cfg.showTownLabels;
                changed = true;
            }
            while (TOGGLE_NATION_LABELS.consumeClick()) {
                cfg.showNationLabels = !cfg.showNationLabels;
                changed = true;
            }
            while (TOGGLE_BUILDING_LABELS.consumeClick()) {
                cfg.showBuildingLabels = !cfg.showBuildingLabels;
                changed = true;
            }
            while (TOGGLE_TRAIN_STATION_LABELS.consumeClick()) {
                cfg.showTrainStationLabels = !cfg.showTrainStationLabels;
                changed = true;
            }

            if (changed) {
                AutoConfig.getConfigHolder(AechronisConfig.class).save();
            }
        });
    }
}
