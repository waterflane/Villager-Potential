package org.waterflane.villager_potential.core;

/**
 * Bounds and tuning for the profession-wide progression multiplier created by trades.
 */
public record ProfessionActivityConfig(
        boolean enabled,
        double minimum,
        double baseline,
        double maximum,
        double increasePerTrade,
        double decayPerTick
) {
    private static final double TRADE_REQUIREMENT_GROWTH_PER_LEVEL = 1.2;

    public ProfessionActivityConfig(
            double minimum,
            double baseline,
            double maximum,
            double increasePerTrade,
            double decayPerTick
    ) {
        this(true, minimum, baseline, maximum, increasePerTrade, decayPerTick);
    }

    public ProfessionActivityConfig {
        if (!Double.isFinite(minimum) || minimum < 0.0) {
            throw new IllegalArgumentException("minimum must be finite and non-negative");
        }
        if (!Double.isFinite(baseline) || baseline <= 0.0 || baseline < minimum) {
            throw new IllegalArgumentException(
                    "baseline must be finite, positive, and at least the minimum"
            );
        }
        if (!Double.isFinite(maximum) || maximum < baseline) {
            throw new IllegalArgumentException("maximum must be finite and at least the baseline");
        }
        if (!Double.isFinite(increasePerTrade) || increasePerTrade < 0.0) {
            throw new IllegalArgumentException("increasePerTrade must be finite and non-negative");
        }
        if (!Double.isFinite(decayPerTick) || decayPerTick < 0.0) {
            throw new IllegalArgumentException("decayPerTick must be finite and non-negative");
        }
    }

    /**
     * Reduces one trade's contribution so each new profession level needs
     * 20% more successful trades to fill the purchase multiplier.
     */
    public double increasePerTradeForLevel(int professionLevel) {
        if (professionLevel < 1 || professionLevel > 5) {
            throw new IllegalArgumentException("professionLevel must be between 1 and 5");
        }
        return increasePerTrade / Math.pow(
                TRADE_REQUIREMENT_GROWTH_PER_LEVEL,
                professionLevel - 1
        );
    }
}
