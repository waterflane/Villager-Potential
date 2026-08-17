package org.waterflane.villager_potential.core;

/**
 * Persistent market demand for one logical trade in one profession.
 *
 * <p>The score is intentionally inert for now: it is recorded for later market
 * behavior but is not a price input. A state only exists after a completed
 * purchase, so offer presentation cannot create demand.</p>
 */
public record MarketDemandState(
        int demandScore,
        long timesPurchased,
        long lastPurchaseGameTime
) {
    public static final int MIN_DEMAND_SCORE = 0;
    public static final int MAX_DEMAND_SCORE = 100;
    public static final int DEMAND_PER_PURCHASE = 1;

    public MarketDemandState {
        if (demandScore < MIN_DEMAND_SCORE || demandScore > MAX_DEMAND_SCORE) {
            throw new IllegalArgumentException(
                    "demandScore must be between "
                            + MIN_DEMAND_SCORE + " and " + MAX_DEMAND_SCORE
            );
        }
        if (timesPurchased < 1L) {
            throw new IllegalArgumentException("timesPurchased must be positive");
        }
    }

    public static MarketDemandState firstPurchaseAt(long gameTime) {
        return new MarketDemandState(DEMAND_PER_PURCHASE, 1L, gameTime);
    }

    public MarketDemandState recordPurchase(long gameTime) {
        return new MarketDemandState(
                Math.min(MAX_DEMAND_SCORE, demandScore + DEMAND_PER_PURCHASE),
                increment(timesPurchased),
                gameTime
        );
    }

    private static long increment(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1L;
    }
}
