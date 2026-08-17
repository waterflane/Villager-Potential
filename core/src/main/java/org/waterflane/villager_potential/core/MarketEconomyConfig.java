package org.waterflane.villager_potential.core;

import java.util.Objects;

/** Loader-neutral demand, pricing and stock policies. */
public record MarketEconomyConfig(
        MarketDemandConfig demand,
        MarketDemandPriceConfig price,
        MarketDemandStockConfig stock
) {
    public MarketEconomyConfig {
        Objects.requireNonNull(demand, "demand");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(stock, "stock");
    }
}
