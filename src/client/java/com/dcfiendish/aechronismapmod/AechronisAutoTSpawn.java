package com.dcfiendish.aechronismapmod;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Optional QoL feature ported from Aechronis Essentials: sends "/t spawn" exactly once per
 * death->respawn transition while connected to Aechronis. Gated on AechronisMapMod.active
 * (set by the existing server-activation JOIN/DISCONNECT handling) rather than a separate
 * server-allowlist check, and on AechronisConfig.autoTSpawn (off by default).
 */
public class AechronisAutoTSpawn {

    /** Tracks whether the player was dead as of the last tick, so a death->alive transition
     *  can be detected exactly once per death and fires on every respawn, not just the first. */
    private static boolean wasDead = false;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || !AechronisMapMod.active || !AechronisConfig.get().autoTSpawn) {
                return;
            }

            if (client.player.isDeadOrDying()) {
                wasDead = true;
            } else if (wasDead) {
                wasDead = false;
                if (client.getConnection() != null) {
                    client.getConnection().sendCommand("t spawn");
                }
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> wasDead = false);
    }
}
