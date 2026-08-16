package org.waterflane.villager_potential.core;

import java.util.OptionalLong;

/**
 * Durable observations for one logical trade.
 *
 * <p>Timestamps are game times. They are optional so a migrated history can
 * retain old aggregate counts even when the old schema did not store when an
 * observation happened.</p>
 */
public record TradeHistory(
        long timesSeen,
        OptionalLong lastSeen,
        long timesUsed,
        OptionalLong lastUsed
) {
    public TradeHistory {
        if (timesSeen < 0L) {
            throw new IllegalArgumentException("timesSeen must not be negative");
        }
        if (timesUsed < 0L) {
            throw new IllegalArgumentException("timesUsed must not be negative");
        }
        if (lastSeen == null) {
            throw new NullPointerException("lastSeen");
        }
        if (lastUsed == null) {
            throw new NullPointerException("lastUsed");
        }
    }

    public static TradeHistory seenAt(long gameTime) {
        return empty().recordSeen(gameTime);
    }

    public static TradeHistory empty() {
        return new TradeHistory(0L, OptionalLong.empty(), 0L, OptionalLong.empty());
    }

    public TradeHistory recordSeen(long gameTime) {
        return new TradeHistory(
                increment(timesSeen),
                OptionalLong.of(gameTime),
                timesUsed,
                lastUsed
        );
    }

    public TradeHistory recordUsed(long gameTime) {
        return new TradeHistory(
                timesSeen,
                lastSeen,
                increment(timesUsed),
                OptionalLong.of(gameTime)
        );
    }

    long mostRecentObservation() {
        long seen = lastSeen.orElse(Long.MIN_VALUE);
        long used = lastUsed.orElse(Long.MIN_VALUE);
        return Math.max(seen, used);
    }

    private static long increment(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1L;
    }
}
