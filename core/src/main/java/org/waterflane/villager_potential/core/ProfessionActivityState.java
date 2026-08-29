package org.waterflane.villager_potential.core;

import java.util.Objects;

/**
 * Minimal restart-safe multiplier state for recent trade activity in one profession.
 *
 * <p>The score is evaluated lazily from its last update time, avoiding
 * per-tick persistent writes.</p>
 */
public record ProfessionActivityState(double score, long lastUpdateGameTime) {
    public ProfessionActivityState {
        if (!Double.isFinite(score) || score < 0.0) {
            throw new IllegalArgumentException("score must be finite and non-negative");
        }
        if (lastUpdateGameTime < 0L) {
            throw new IllegalArgumentException("lastUpdateGameTime must not be negative");
        }
    }

    public static ProfessionActivityState recordFirstTrade(
            long gameTime,
            ProfessionActivityConfig config,
            double increasePerTrade
    ) {
        Objects.requireNonNull(config, "config");
        validateGameTime(gameTime);
        validateIncrease(increasePerTrade);
        return new ProfessionActivityState(
                Math.min(config.maximum(), config.baseline() + increasePerTrade),
                gameTime
        );
    }

    /**
     * Returns the activity multiplier after elapsed game time has moved it
     * toward the configured baseline and all bounds have been applied.
     */
    public double scoreAt(long gameTime, ProfessionActivityConfig config) {
        Objects.requireNonNull(config, "config");
        validateGameTime(gameTime);

        double boundedScore = Math.max(config.minimum(), Math.min(config.maximum(), score));
        long elapsedTicks = gameTime <= lastUpdateGameTime
                ? 0L
                : gameTime - lastUpdateGameTime;
        double movement = config.decayPerTick() * elapsedTicks;
        if (boundedScore > config.baseline()) {
            return Math.max(config.baseline(), boundedScore - movement);
        }
        return Math.min(config.baseline(), boundedScore + movement);
    }

    /**
     * Applies decay first, then records one completed trade up to the cap.
     */
    public ProfessionActivityState recordTrade(
            long gameTime,
            ProfessionActivityConfig config,
            double increasePerTrade
    ) {
        Objects.requireNonNull(config, "config");
        validateGameTime(gameTime);
        validateIncrease(increasePerTrade);
        long effectiveGameTime = Math.max(gameTime, lastUpdateGameTime);
        double increasedScore = Math.min(
                config.maximum(),
                scoreAt(effectiveGameTime, config) + increasePerTrade
        );
        if (increasedScore == score && effectiveGameTime == lastUpdateGameTime) {
            return this;
        }
        return new ProfessionActivityState(increasedScore, effectiveGameTime);
    }

    private static void validateGameTime(long gameTime) {
        if (gameTime < 0L) {
            throw new IllegalArgumentException("gameTime must not be negative");
        }
    }

    private static void validateIncrease(double increasePerTrade) {
        if (!Double.isFinite(increasePerTrade) || increasePerTrade < 0.0) {
            throw new IllegalArgumentException(
                    "increasePerTrade must be finite and non-negative"
            );
        }
    }
}
