package org.waterflane.villager_potential.core;

import java.util.Objects;

/**
 * Persistent market demand for one logical trade in one profession.
 *
 * <p>The persisted score is evaluated lazily from the last purchase's server
 * time. Merely reading demand never rewrites this state, and a state only
 * exists after a completed purchase.</p>
 */
public record MarketDemandState(
        double demandScore,
        long timesPurchased,
        long lastPurchaseGameTime
) {
    public static final double MIN_DEMAND_SCORE = MarketDemandConfig.DEFAULT.minimum();
    public static final double MAX_DEMAND_SCORE = MarketDemandConfig.DEFAULT.maximum();
    public static final double DEMAND_PER_PURCHASE =
            MarketDemandConfig.DEFAULT.increasePerPurchase();

    public MarketDemandState {
        if (!Double.isFinite(demandScore)) {
            throw new IllegalArgumentException("demandScore must be finite");
        }
        if (timesPurchased < 1L) {
            throw new IllegalArgumentException("timesPurchased must be positive");
        }
        validateGameTime(lastPurchaseGameTime);
    }

    public static MarketDemandState firstPurchaseAt(long gameTime) {
        return firstPurchaseAt(gameTime, MarketDemandConfig.DEFAULT);
    }

    public static MarketDemandState firstPurchaseAt(
            long gameTime,
            MarketDemandConfig config
    ) {
        Objects.requireNonNull(config, "config");
        validateGameTime(gameTime);
        return new MarketDemandState(
                Math.min(config.maximum(), config.baseline() + config.increasePerPurchase()),
                1L,
                gameTime
        );
    }

    /**
     * Resolves demand after elapsed server/profession time moves it toward the
     * configured baseline. This calculation is pure and does not materialize a
     * per-tick update.
     */
    public double scoreAt(long gameTime, MarketDemandConfig config) {
        Objects.requireNonNull(config, "config");
        validateGameTime(gameTime);
        if (!config.enabled()) {
            return config.baseline();
        }

        double boundedScore = clamp(demandScore, config.minimum(), config.maximum());
        long elapsedTicks = gameTime <= lastPurchaseGameTime
                ? 0L
                : gameTime - lastPurchaseGameTime;
        double movement = config.decayPerTick() * elapsedTicks;
        if (boundedScore > config.baseline()) {
            return Math.max(config.baseline(), boundedScore - movement);
        }
        return Math.min(config.baseline(), boundedScore + movement);
    }

    public MarketDemandState recordPurchase(long gameTime) {
        return recordPurchase(gameTime, MarketDemandConfig.DEFAULT);
    }

    /** Applies lazy decay before recording the completed purchase. */
    public MarketDemandState recordPurchase(long gameTime, MarketDemandConfig config) {
        Objects.requireNonNull(config, "config");
        validateGameTime(gameTime);
        if (!config.enabled()) {
            return this;
        }
        long effectiveGameTime = Math.max(gameTime, lastPurchaseGameTime);
        return new MarketDemandState(
                Math.min(
                        config.maximum(),
                        scoreAt(effectiveGameTime, config) + config.increasePerPurchase()
                ),
                increment(timesPurchased),
                effectiveGameTime
        );
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long increment(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1L;
    }

    private static void validateGameTime(long gameTime) {
        if (gameTime < 0L) {
            throw new IllegalArgumentException("gameTime must not be negative");
        }
    }
}
