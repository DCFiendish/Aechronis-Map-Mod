package com.example;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses [War] chat messages and updates:
 * 1. AechronisMapData (existing map overlay data)
 * 2. AechronisWarHudData (new War HUD defense/offense tables)
 */
public class AechronisChatListener {

    // [War] PlayerName is attacking TownName at (bx, by, bz)
    private static final Pattern ATTACK_START = Pattern.compile(
        "\[War\] (.+?) is attacking (.+?) at \(([-\d]+), ([-\d]+), ([-\d]+)\)"
    );

    // [War] PlayerName is liberating TownName at (bx, by, bz)
    private static final Pattern LIBERATE_START = Pattern.compile(
        "\[War\] (.+?) is liberating (.+?) at \(([-\d]+), ([-\d]+), ([-\d]+)\)"
    );

    // [War] Attack at (bx, by, bz) defeated by PlayerName
    private static final Pattern ATTACK_DEFEATED = Pattern.compile(
        "\[War\] Attack at \(([-\d]+), ([-\d]+), ([-\d]+)\) defeated by .+"
    );

    // [War] Attack at (bx, by, bz) stopped by an explosion
    private static final Pattern ATTACK_EXPLOSION = Pattern.compile(
        "\[War\] Attack at \(([-\d]+), ([-\d]+), ([-\d]+)\) stopped by an explosion"
    );

    // [War] TownName captured chunk (cx, cz) from TownName
    private static final Pattern CHUNK_CAPTURED = Pattern.compile(
        "\[War\] (.+?) captured chunk \(([-\d]+), ([-\d]+)\) from (.+)"
    );

    // [War] TownName liberated chunk (cx, cz) from TownName
    private static final Pattern CHUNK_LIBERATED = Pattern.compile(
        "\[War\] (.+?) liberated chunk \(([-\d]+), ([-\d]+)\) from (.+)"
    );

    // [War] TownName defended chunk (cx, cz) against TownName
    private static final Pattern CHUNK_DEFENDED = Pattern.compile(
        "\[War\] (.+?) defended chunk \(([-\d]+), ([-\d]+)\) against (.+)"
    );

    // [War] TownName captured territory (id=N)
    private static final Pattern TERRITORY_CAPTURED = Pattern.compile(
        "\[War\] (.+?) captured territory \(id=(\d+)\)"
    );

    // [War] TownName liberated territory (id=N)
    private static final Pattern TERRITORY_LIBERATED = Pattern.compile(
        "\[War\] (.+?) liberated territory \(id=(\d+)\)"
    );

    private final AechronisMapData mapData;
    private AechronisConfig config;

    public AechronisChatListener(AechronisMapData mapData) {
        this.mapData = mapData;
    }

    public void register() {
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            handleMessage(message);
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) handleMessage(message);
        });
    }

    private void handleMessage(Component message) {
        String raw = message.getString();
        // Strip color codes
        String text = raw.replaceAll("\u00a7[0-9a-fk-orA-FK-OR]", "");
        if (!text.contains("[War]")) return;

        config = AechronisConfig.get();
        Matcher m;

        // Attack start
        m = ATTACK_START.matcher(text);
        if (m.find()) {
            String player = m.group(1).trim();
            String defendingTown = m.group(2).trim();
            int bx = Integer.parseInt(m.group(3));
            int bz = Integer.parseInt(m.group(5));
            int cx = bx >> 4;
            int cz = bz >> 4;

            // Extract attacker's town from [TownName] prefix in player name
            String attackerTown = extractTown(player);
            String cleanPlayer = cleanPlayerName(player);

            // Existing map data
            mapData.beginAttack(cx, cz, attackerTown);

            // War HUD — determine defense vs offense
            String locationLabel = AechronisTerritoryHelper.getLocationLabel(cx, cz);
            boolean isCore = AechronisWarHudData.isCoreChunk(cx, cz);
            boolean isHome = AechronisWarHudData.isHomeChunk(cx, cz);

            if (AechronisWarHudData.isKoreaChunk(cx, cz)) {
                // Defense: enemy attacking Korea
                AechronisWarHudData.addDefenseCap(cx, cz, cleanPlayer, attackerTown, locationLabel, isCore, isHome, config);
            } else if (isKoreaTown(attackerTown)) {
                // Offense: Korea attacking enemy
                AechronisWarHudData.addOffenseCap(cx, cz, cleanPlayer, defendingTown, locationLabel, config);
            }
            return;
        }

        // Liberate start (treat same as attack for HUD)
        m = LIBERATE_START.matcher(text);
        if (m.find()) {
            String player = m.group(1).trim();
            String defendingTown = m.group(2).trim();
            int bx = Integer.parseInt(m.group(3));
            int bz = Integer.parseInt(m.group(5));
            int cx = bx >> 4;
            int cz = bz >> 4;
            String attackerTown = extractTown(player);
            String cleanPlayer = cleanPlayerName(player);

            mapData.beginAttack(cx, cz, attackerTown);

            String locationLabel = AechronisTerritoryHelper.getLocationLabel(cx, cz);
            boolean isCore = AechronisWarHudData.isCoreChunk(cx, cz);
            boolean isHome = AechronisWarHudData.isHomeChunk(cx, cz);

            if (AechronisWarHudData.isKoreaChunk(cx, cz)) {
                AechronisWarHudData.addDefenseCap(cx, cz, cleanPlayer, attackerTown, locationLabel, isCore, isHome, config);
            } else if (isKoreaTown(attackerTown)) {
                AechronisWarHudData.addOffenseCap(cx, cz, cleanPlayer, defendingTown, locationLabel, config);
            }
            return;
        }

        // Attack defeated
        m = ATTACK_DEFEATED.matcher(text);
        if (m.find()) {
            int bx = Integer.parseInt(m.group(1));
            int bz = Integer.parseInt(m.group(3));
            int cx = bx >> 4; int cz = bz >> 4;
            mapData.cancelAttack(cx, cz);
            AechronisWarHudData.removeDefenseCap(cx, cz);
            AechronisWarHudData.removeOffenseCap(cx, cz);
            return;
        }

        // Attack stopped by explosion
        m = ATTACK_EXPLOSION.matcher(text);
        if (m.find()) {
            int bx = Integer.parseInt(m.group(1));
            int bz = Integer.parseInt(m.group(3));
            int cx = bx >> 4; int cz = bz >> 4;
            mapData.cancelAttack(cx, cz);
            AechronisWarHudData.removeDefenseCap(cx, cz);
            AechronisWarHudData.removeOffenseCap(cx, cz);
            return;
        }

        // Chunk captured
        m = CHUNK_CAPTURED.matcher(text);
        if (m.find()) {
            int cx = Integer.parseInt(m.group(2));
            int cz = Integer.parseInt(m.group(3));
            mapData.captureChunk(cx, cz);
            AechronisWarHudData.removeDefenseCap(cx, cz);
            AechronisWarHudData.removeOffenseCap(cx, cz);
            return;
        }

        // Chunk liberated
        m = CHUNK_LIBERATED.matcher(text);
        if (m.find()) {
            int cx = Integer.parseInt(m.group(2));
            int cz = Integer.parseInt(m.group(3));
            mapData.liberateChunk(cx, cz);
            AechronisWarHudData.removeDefenseCap(cx, cz);
            AechronisWarHudData.removeOffenseCap(cx, cz);
            return;
        }

        // Chunk defended
        m = CHUNK_DEFENDED.matcher(text);
        if (m.find()) {
            int cx = Integer.parseInt(m.group(2));
            int cz = Integer.parseInt(m.group(3));
            AechronisWarHudData.removeDefenseCap(cx, cz);
            AechronisWarHudData.removeOffenseCap(cx, cz);
            return;
        }

        // Territory captured/liberated
        m = TERRITORY_CAPTURED.matcher(text);
        if (m.find()) {
            mapData.captureTerritory(Integer.parseInt(m.group(2)));
            return;
        }
        m = TERRITORY_LIBERATED.matcher(text);
        if (m.find()) {
            mapData.captureTerritory(Integer.parseInt(m.group(2)));
        }
    }

    // Extract [TownName] from a player name like "[Houston] Slambam115_"
    private static String extractTown(String playerWithPrefix) {
        if (playerWithPrefix.startsWith("[") && playerWithPrefix.contains("]")) {
            return playerWithPrefix.substring(1, playerWithPrefix.indexOf("]")).trim();
        }
        return "";
    }

    // Get just the player username without [TownName] prefix
    private static String cleanPlayerName(String playerWithPrefix) {
        if (playerWithPrefix.startsWith("[") && playerWithPrefix.contains("]")) {
            return playerWithPrefix.substring(playerWithPrefix.indexOf("]") + 1).trim();
        }
        return playerWithPrefix;
    }

    // Korea's towns — Milano is the current Korea town
    private static boolean isKoreaTown(String town) {
        return "Milano".equalsIgnoreCase(town) || "Korea".equalsIgnoreCase(town);
    }
}
