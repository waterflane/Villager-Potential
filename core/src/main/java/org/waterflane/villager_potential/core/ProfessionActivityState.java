package org.waterflane.villager_potential.core;

import java.util.Objects;

/**
 * Minimal restart-safe state for recent trade activity in one profession.
 *
 * <p>The score is evaluated lazily from its last update time, avoiding
 * per-tick persistent writes.</p>
 */
public record ProfessionActivityState(double score, long lastUpdateGameTime) {
    public static final double MINIMUM_SCORE = 0.0;
    public static final double MAXIMUM_SCORE = 1.0;

    public ProfessionActivityState {
        if (!Double.isFinite(score) || score < MINIMUM_SCORE || score > MAXIMUM_SCORE) {
            throw new IllegalArgumentException("score must be finite and between 0.0 and 1.0");
        }
        if (lastUpdateGameTime < 0L) {
            throw new IllegalArgumentException("lastUpdateGameTime must not be negative");
        }
    }

    public static ProfessionActivityState recordFirstTrade(
            long gameTime,
            ProfessionActivityConfig config
    ) {
        Objects.requireNonNull(config, "config");
        validateGameTime(gameTime);
        return new ProfessionActivityState(
                Math.min(config.maximum(), config.baseline() + config.increasePerTrade()),
                gameTime
        );
    }

    /**
     * Returns the score after elapsed game time has moved it toward baseline.
     */
    public double scoreAt(long gameTime, ProfessionActivityConfig config) {
        Objects.requireNonNull(config, "config");
        validateGameTime(gameTime);

        double boundedScore = Math.max(config.baseline(), Math.min(config.maximum(), score));
        long elapsedTicks = gameTime <= lastUpdateGameTime
                ? 0L
                : gameTime - lastUpdateGameTime;
        double decay = config.decayPerTick() * elapsedTicks;
        return Math.max(config.baseline(), boundedScore - decay);
    }

    /**
     * Applies decay first, then records one completed trade up to the cap.
     */
    public ProfessionActivityState recordTrade(
            long gameTime,
            ProfessionActivityConfig config
    ) {
        Objects.requireNonNull(config, "config");
        validateGameTime(gameTime);
        long effectiveGameTime = Math.max(gameTime, lastUpdateGameTime);
        double increasedScore = Math.min(
                config.maximum(),
                scoreAt(effectiveGameTime, config) + config.increasePerTrade()
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
}
