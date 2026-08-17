package org.waterflane.villager_potential.core;

/** Server-controlled bounds for demand-sensitive offer stock. */
public record MarketDemandStockConfig(
        boolean enabled,
        int maximumAdditionalUses,
        int maximumUsesPerOffer
) {
    public static final MarketDemandStockConfig DISABLED =
            new MarketDemandStockConfig(false, 2, 16);

    public MarketDemandStockConfig {
        if (maximumAdditionalUses < 0) {
            throw new IllegalArgumentException("maximumAdditionalUses must be non-negative");
        }
        if (maximumUsesPerOffer < 1) {
            throw new IllegalArgumentException("maximumUsesPerOffer must be positive");
        }
    }
}
