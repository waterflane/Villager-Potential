package org.waterflane.villager_potential.core;

/** Demand curve and hard percentage cap for non-emerald payment adjustments. */
public record MarketDemandPriceConfig(
        boolean enabled,
        double minimumMultiplier,
        double maximumMultiplier,
        double maximumItemPaymentIncrease,
        double demandScoreForMaximumPrice
) {
    public static final MarketDemandPriceConfig DEFAULT =
            new MarketDemandPriceConfig(true, 1.0, 2.0, 0.125, 8.0);

    public MarketDemandPriceConfig(
            boolean enabled,
            double minimumMultiplier,
            double maximumMultiplier
    ) {
        this(
                enabled,
                minimumMultiplier,
                maximumMultiplier,
                DEFAULT.maximumItemPaymentIncrease(),
                DEFAULT.demandScoreForMaximumPrice()
        );
    }

    public MarketDemandPriceConfig(double minimumMultiplier, double maximumMultiplier) {
        this(
                true,
                minimumMultiplier,
                maximumMultiplier,
                DEFAULT.maximumItemPaymentIncrease(),
                DEFAULT.demandScoreForMaximumPrice()
        );
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
        validatePercentage("maximumItemPaymentIncrease", maximumItemPaymentIncrease);
        if (!Double.isFinite(demandScoreForMaximumPrice)
                || demandScoreForMaximumPrice <= 0.0) {
            throw new IllegalArgumentException(
                    "demandScoreForMaximumPrice must be finite and positive"
            );
        }
    }

    private static void validatePercentage(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and between 0 and 1");
        }
    }
}
