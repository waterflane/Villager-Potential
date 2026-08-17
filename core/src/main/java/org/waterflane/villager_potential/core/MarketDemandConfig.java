package org.waterflane.villager_potential.core;

/**
 * Bounds and tuning for demand attached to one logical trade.
 *
 * <p>All time values are server/profession ticks supplied by the caller. No
 * wall-clock time participates in demand evaluation.</p>
 */
public record MarketDemandConfig(
        double minimum,
        double baseline,
        double maximum,
        double increasePerPurchase,
        double decayPerTick
) {
    public static final MarketDemandConfig DEFAULT = new MarketDemandConfig(
            0.0,
            0.0,
            100.0,
            1.0,
            1.0 / 1_200.0
    );

    public MarketDemandConfig {
        if (!Double.isFinite(minimum)) {
            throw new IllegalArgumentException("minimum must be finite");
        }
        if (!Double.isFinite(baseline) || baseline < minimum) {
            throw new IllegalArgumentException(
                    "baseline must be finite and at least the minimum"
            );
        }
        if (!Double.isFinite(maximum) || maximum < baseline) {
            throw new IllegalArgumentException(
                    "maximum must be finite and at least the baseline"
            );
        }
        if (!Double.isFinite(increasePerPurchase) || increasePerPurchase <= 0.0) {
            throw new IllegalArgumentException(
                    "increasePerPurchase must be finite and positive"
            );
        }
        if (!Double.isFinite(decayPerTick) || decayPerTick < 0.0) {
            throw new IllegalArgumentException(
                    "decayPerTick must be finite and non-negative"
            );
        }
    }
}
