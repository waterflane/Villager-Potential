package org.waterflane.villager_potential.core;

/** Shared Minecraft clock constants used by progression on every platform. */
public final class MinecraftTime {
    public static final long TICKS_PER_SECOND = 20L;
    public static final long SECONDS_PER_MINUTE = 60L;
    public static final long TICKS_PER_MINUTE = TICKS_PER_SECOND * SECONDS_PER_MINUTE;
    public static final long TICKS_PER_DAY = 24_000L;
    public static final long DAYLIGHT_TICKS = 12_000L;

    private MinecraftTime() {
    }

    /** Daytime is the first half of Minecraft's repeating 24,000-tick day. */
    public static boolean isDaytime(long dayTime) {
        return Math.floorMod(dayTime, TICKS_PER_DAY) < DAYLIGHT_TICKS;
    }
}
