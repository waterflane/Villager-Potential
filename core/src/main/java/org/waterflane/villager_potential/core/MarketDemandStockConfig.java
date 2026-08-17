package org.waterflane.villager_potential.core;

/** Server-controlled bounds for demand-sensitive offer stock. */
public record MarketDemandStockConfig(
        boolean enabled,
        double influenceStrength,
        int maximumAdditionalUses,
        int maximumUsesPerOffer
) {
    public static final MarketDemandStockConfig DISABLED =
            new MarketDemandStockConfig(false, 1.0, 2, 16);

    public MarketDemandStockConfig(
            boolean enabled,
            int maximumAdditionalUses,
            int maximumUsesPerOffer
    ) {
        this(enabled, 1.0, maximumAdditionalUses, maximumUsesPerOffer);
    }

    public MarketDemandStockConfig {
        if (!Double.isFinite(influenceStrength)
                || influenceStrength < 0.0
                || influenceStrength > 1.0) {
            throw new IllegalArgumentException(
                    "influenceStrength must be between zero and one"
            );
        }
        if (maximumAdditionalUses < 0) {
            throw new IllegalArgumentException("maximumAdditionalUses must be non-negative");
        }
        if (maximumUsesPerOffer < 1) {
            throw new IllegalArgumentException("maximumUsesPerOffer must be positive");
        }
    }
}
