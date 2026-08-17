package org.waterflane.villager_potential.core;

/** Bounds for the price multiplier produced by market demand. */
public record MarketDemandPriceConfig(
        boolean enabled,
        double minimumMultiplier,
        double maximumMultiplier
) {
    public static final MarketDemandPriceConfig DEFAULT =
            new MarketDemandPriceConfig(true, 1.0, 2.0);

    public MarketDemandPriceConfig(double minimumMultiplier, double maximumMultiplier) {
        this(true, minimumMultiplier, maximumMultiplier);
    }

    public MarketDemandPriceConfig {
        if (!Double.isFinite(minimumMultiplier)
                || minimumMultiplier <= 0.0
                || minimumMultiplier > 1.0) {
            throw new IllegalArgumentException(
                    "minimumMultiplier must be finite, positive, and at most 1"
            );
        }
        if (!Double.isFinite(maximumMultiplier)
                || maximumMultiplier < 1.0) {
            throw new IllegalArgumentException(
                    "maximumMultiplier must be finite and at least 1"
            );
        }
    }
}
