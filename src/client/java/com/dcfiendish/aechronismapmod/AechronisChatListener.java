package com.dcfiendish.aechronismapmod;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AechronisChatListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("Aechronis");

    // Node-level (whole-territory) flips, plus per-chunk war tracking (chunk
    // captured/liberated/defended, attack-start/under-attack). Per-chunk tracking
    // was previously removed per a server admin ruling restricting the map to
    // node-by-node changes; that ruling no longer applies, so it's reinstated here.
    //
    // Group 1 is always the acting PLAYER'S username, not a town — confirmed against
    // the plugin source (FlagWar.kt / NodesWorldListener.kt: every [War] broadcast
    // interpolates ${attacker?.name} / ${event.player.name}, i.e. a player, never a
    // town). Resolved to a nation via AechronisMapData.playerNationMap, not
    // townNationMap, everywhere below.
    private static final Pattern TERRITORY_CAPTURED = Pattern.compile(
            "\\[War\\] (.+?) captured territory \\(id=(\\d+)\\)"
    );
    private static final Pattern TERRITORY_LIBERATED = Pattern.compile(
            "\\[War\\] (.+?) liberated territory \\(id=(\\d+)\\)"
    );

    // Per-chunk war tracking. Formats verified directly against Aechronis's own
    // github.com/Aechronis/nodes plugin source (FlagWar.kt / NodesWorldListener.kt)
    // rather than assumed. Every format below has a confirmed live broadcast site
    // except ATTACK_EXPLOSION ("stopped by an explosion") — no such broadcast exists
    // anywhere in the current Aechronis/nodes source, so that pattern is currently
    // dead code on this server (kept in case a future plugin update reintroduces it):
    //   "[War] X is attacking Y at (bx, by, bz)"       — block coords, shift >>4 for chunk.
    //   "[War] X is liberating Y at (bx, by, bz)"      — same, block coords.
    //   "[War] Attack at (bx, by, bz) defeated by P"   — block coords, no actor needed.
    //   "[War] Attack at (bx, by, bz) stopped by an explosion" — block coords, no actor.
    //   "[War] X captured chunk (cx, cz) from Y!"      — CHUNK coords directly, trailing "!".
    //   "[War] X liberated chunk (cx, cz) from Y!"     — chunk coords, trailing "!".
    //   "[War] X defended chunk (cx, cz) against Y!"   — chunk coords, trailing "!".
    // The trailing "!" and the "from/against Y" town name are never relied on below —
    // only group 1 (attacker) and the coordinate groups are used.
    private static final Pattern ATTACK_START = Pattern.compile(
            "\\[War\\] (.+?) is attacking (.+?) at \\(([-\\d]+), ([-\\d]+), ([-\\d]+)\\)"
    );
    private static final Pattern LIBERATE_START = Pattern.compile(
            "\\[War\\] (.+?) is liberating (.+?) at \\(([-\\d]+), ([-\\d]+), ([-\\d]+)\\)"
    );
    private static final Pattern ATTACK_DEFEATED = Pattern.compile(
            "\\[War\\] Attack at \\(([-\\d]+), ([-\\d]+), ([-\\d]+)\\) defeated by .+"
    );
    private static final Pattern ATTACK_EXPLOSION = Pattern.compile(
            "\\[War\\] Attack at \\(([-\\d]+), ([-\\d]+), ([-\\d]+)\\) stopped by an explosion"
    );
    private static final Pattern CHUNK_CAPTURED = Pattern.compile(
            "\\[War\\] (.+?) captured chunk \\(([-\\d]+), ([-\\d]+)\\) from (.+)"
    );
    private static final Pattern CHUNK_LIBERATED = Pattern.compile(
            "\\[War\\] (.+?) liberated chunk \\(([-\\d]+), ([-\\d]+)\\) from (.+)"
    );
    private static final Pattern CHUNK_DEFENDED = Pattern.compile(
            "\\[War\\] (.+?) defended chunk \\(([-\\d]+), ([-\\d]+)\\) against (.+)"
    );

    private final AechronisMapData mapData;

    public AechronisChatListener(AechronisMapData mapData) {
        this.mapData = mapData;
    }

    public void register() {
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
                handleMessage(message));
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) handleMessage(message);
        });
    }

    private void handleMessage(Component message) {
        String text = message.getString().replaceAll("\u00a7[0-9a-fk-orA-FK-OR]", "");
        AechronisWarCapture.logChatLine(text); // no-op unless AechronisWarCapture.ENABLED
        if (!text.contains("[War]")) return;
        // This listener registers unconditionally on every server the client connects
        // to, not just Aechronis \u2014 reuse the same "is the Aechronis-gated renderer
        // actually active" signal AechronisDrawManagerMixin uses, so a coincidental
        // [War]-shaped line on an unrelated server (different plugin, different
        // context) never touches mapData at all.
        if (AechronisRenderer.ourFeatures.isEmpty()) return;

        try {
            dispatch(text);
        } catch (Exception e) {
            // A malformed/unexpected match (regex matched but content didn't parse as
            // expected, a future Nodes plugin format change, etc.) must never propagate
            // an uncaught exception through Fabric's chat event pipeline.
            LOGGER.warn("Chat handler error on line: {}", text, e);
        }
    }

    private void dispatch(String text) {
        Matcher m;

        // Attack/liberate start — a flag was just planted on this chunk. Block coords
        // (bx, by, bz); group 3 is bx, group 5 is bz (group 4, by, is unused). Both
        // patterns share identical handling — only the verb differs in the broadcast.
        // NOTE: `m.find()` must be called exactly once per Matcher — calling it a second
        // time on the same Matcher searches for a SECOND occurrence later in the string
        // instead of re-checking the first, so each pattern gets its own `find()` call
        // and the result is captured in `matched` rather than re-tested.
        m = ATTACK_START.matcher(text);
        boolean matched = m.find();
        if (!matched) {
            m = LIBERATE_START.matcher(text);
            matched = m.find();
        }
        if (matched) {
            int bx = Integer.parseInt(m.group(3));
            int bz = Integer.parseInt(m.group(5));
            mapData.beginAttack(bx >> 4, bz >> 4, extractPlayerName(m.group(1).trim()));
            return;
        }

        // Attack ended without a chunk changing hands (defeated, or explosion) — clear
        // the under-attack marker on the chunk the attack's block position resolves to.
        // Both patterns share identical handling (see NOTE above re: find() semantics).
        m = ATTACK_DEFEATED.matcher(text);
        matched = m.find();
        if (!matched) {
            m = ATTACK_EXPLOSION.matcher(text);
            matched = m.find();
        }
        if (matched) {
            int bx = Integer.parseInt(m.group(1));
            int bz = Integer.parseInt(m.group(3));
            mapData.cancelAttack(bx >> 4, bz >> 4);
            return;
        }

        // Chunk captured — single-chunk recolor + war stripe. Chunk coords directly
        // (no >>4 shift — unlike the attack/liberate messages above, these already are
        // chunk coordinates per FlagWar.kt's chunk.coord.x/z).
        m = CHUNK_CAPTURED.matcher(text);
        if (m.find()) {
            int cx = Integer.parseInt(m.group(2));
            int cz = Integer.parseInt(m.group(3));
            mapData.captureChunk(cx, cz, extractPlayerName(m.group(1).trim()));
            return;
        }

        // Chunk liberated — clears war/attack state on that chunk (does not recolor).
        m = CHUNK_LIBERATED.matcher(text);
        if (m.find()) {
            int cx = Integer.parseInt(m.group(2));
            int cz = Integer.parseInt(m.group(3));
            mapData.liberateChunk(cx, cz);
            return;
        }

        // Chunk defended — the attacker failed to take the chunk. Per the plugin source
        // this is a finishAttack() outcome exactly like captured/liberated (the flag's
        // resolution either way), so it must also clear the under-attack marker.
        m = CHUNK_DEFENDED.matcher(text);
        if (m.find()) {
            int cx = Integer.parseInt(m.group(2));
            int cz = Integer.parseInt(m.group(3));
            mapData.cancelAttack(cx, cz);
            return;
        }

        // Territory captured — flips the whole node to the capturing player's nation color.
        m = TERRITORY_CAPTURED.matcher(text);
        if (m.find()) {
            String capturingPlayer = extractPlayerName(m.group(1).trim());
            mapData.captureTerritory(m.group(2), capturingPlayer);
            return;
        }

        // Territory liberated — original owner (or ally) reclaimed it; clear the
        // occupied marker and flip the base color immediately (see liberateTerritory
        // javadoc — this is NOT a new capture, so it must not set the diagonal).
        m = TERRITORY_LIBERATED.matcher(text);
        if (m.find()) {
            String liberatingPlayer = extractPlayerName(m.group(1).trim());
            mapData.liberateTerritory(m.group(2), liberatingPlayer);
        }
    }

    // Defensive bracket-strip in case a nickname/prefix plugin ever wraps the name
    // (e.g. "[Tag] Name") — the vanilla broadcast today is a bare username.
    private static String extractPlayerName(String name) {
        if (name.startsWith("[") && name.contains("]"))
            return name.substring(1, name.indexOf("]")).trim();
        return name;
    }
}