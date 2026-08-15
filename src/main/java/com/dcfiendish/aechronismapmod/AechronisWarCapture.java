package com.dcfiendish.aechronismapmod;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dormant war-day forensics tool. Everything here is gated behind {@link #ENABLED};
 * while false, every method is a single boolean-check no-op, so leaving this wired
 * in year-round costs nothing. To activate: flip ENABLED to true and rebuild.
 *
 * Three call sites are already wired:
 *   1. AechronisChatListener.handleMessage() — every raw [War] chat line, verbatim.
 *   2. AechronisMapData.loadTownsData() — a towns.json snapshot whenever the occupied
 *      (captured-not-annexed) set changes, plus a state-log line per poll summary.
 *   3. AechronisMapData.captureTerritory() / annexTerritory() — a state-log line per
 *      chat-driven flip, so it can be correlated against the chat transcript and the
 *      poll summaries to answer "what did the mod think was happening, and when."
 *
 * Output lands in <game dir>/aechronis-warcapture/:
 *   chat.log        — every [War] line seen, timestamped.
 *   first-seen.log  — first occurrence of each distinct message SHAPE this session
 *                      (numbers collapsed to '#'), for confirming regexes against
 *                      messages we don't have a handler for yet.
 *   state.log       — mod-side transitions (captureTerritory/annexTerritory calls,
 *                      poll summaries), for correlating against chat.log.
 *   snapshots/       — full towns.json bodies, one per occupied-set transition.
 */
public final class AechronisWarCapture {

    public static final boolean ENABLED = false;

    private static final Path DIR = FabricLoader.getInstance().getGameDir().resolve("aechronis-warcapture");
    private static final Path CHAT_LOG = DIR.resolve("chat.log");
    private static final Path STATE_LOG = DIR.resolve("state.log");
    private static final Path FIRST_SEEN_LOG = DIR.resolve("first-seen.log");
    private static final Path SNAPSHOT_DIR = DIR.resolve("snapshots");

    private static final Set<String> seenTemplates = ConcurrentHashMap.newKeySet();

    private AechronisWarCapture() {}

    /** Call for every chat line seen by the client. Filters to [War] lines internally. */
    public static void logChatLine(String rawText) {
        if (!ENABLED) return;
        if (!rawText.contains("[War]")) return;
        append(CHAT_LOG, rawText);
        if (seenTemplates.add(templatize(rawText))) {
            append(FIRST_SEEN_LOG, rawText);
        }
    }

    /** Call for mod-side state transitions (captureTerritory/annexTerritory calls,
     *  poll summaries) so they can be correlated against chat.log by timestamp. */
    public static void logState(String event) {
        if (!ENABLED) return;
        append(STATE_LOG, event);
    }

    /** Call from loadTownsData() with the raw (pre-parse) towns.json body whenever the
     *  occupied set is about to change, plus a short reason tag for the filename. */
    public static void snapshotTownsJson(String rawJson, String reason) {
        if (!ENABLED) return;
        synchronized (AechronisWarCapture.class) {
            try {
                Files.createDirectories(SNAPSHOT_DIR);
                String filename = Instant.now().toString().replace(":", "-") + "_" + reason + ".json";
                Files.writeString(SNAPSHOT_DIR.resolve(filename), rawJson);
            } catch (IOException e) {
                System.out.println("[Aechronis] WarCapture snapshot failed: " + e.getMessage());
            }
        }
    }

    private static synchronized void append(Path file, String line) {
        try {
            Files.createDirectories(DIR);
            Files.writeString(file, "[" + Instant.now() + "] " + line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("[Aechronis] WarCapture log failed: " + e.getMessage());
        }
    }

    /** Collapses digits so e.g. "captured territory (id=5)" and "(id=12)" count as
     *  one shape for first-occurrence tracking. Player/town names are left alone —
     *  they vary too much to normalize usefully. */
    private static String templatize(String s) {
        return s.replaceAll("\\d+", "#");
    }
}
