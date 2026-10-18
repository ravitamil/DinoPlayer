package com.brouken.player;

/** Progress presentation uses completion independently of the current rewatch position. */
public final class WatchProgress {
    private WatchProgress() { }

    public static int percent(long position, long duration, boolean completed) {
        if (completed) return 100;
        if (position <= 0 || duration <= 0) return 0;
        return (int) Math.min(100, position * 100.0 / duration);
    }

    public static long resumePosition(long position, long duration) {
        return duration > 0 && position >= duration ? 0 : Math.max(0, position);
    }
}
