package com.example;

/**
 * Represents a single active cap event shown in the War HUD.
 */
public class CapEntry {
    public final int cx;
    public final int cz;
    public final String playerName;
    public final String townName;
    public final String locationLabel;
    public final boolean isCore;
    public final boolean isHome;
    public final long startTimeMs;
    public final int capTimeSeconds;

    public CapEntry(int cx, int cz, String playerName, String townName,
                    String locationLabel, boolean isCore, boolean isHome, int capTimeSeconds) {
        this.cx = cx;
        this.cz = cz;
        this.playerName = playerName;
        this.townName = townName;
        this.locationLabel = locationLabel;
        this.isCore = isCore;
        this.isHome = isHome;
        this.startTimeMs = System.currentTimeMillis();
        this.capTimeSeconds = capTimeSeconds;
    }

    public long getRemainingSeconds() {
        long elapsedMs = System.currentTimeMillis() - startTimeMs;
        long elapsedSec = elapsedMs / 1000;
        return Math.max(0, capTimeSeconds - elapsedSec);
    }
}
