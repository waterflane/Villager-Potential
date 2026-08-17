package org.waterflane.villager_potential.core;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Loader-neutral configuration for specializations, palettes and dynamic economy. */
public record VillagerTradeConfig(
        SpecializationConfig specializations,
        TradePaletteConfig palette,
        MarketEconomyConfig economy
) {
    public static final VillagerTradeConfig DEFAULT = new VillagerTradeConfig(
            new SpecializationConfig(true, 1.0, 0.1, 1.0, 2.0, Map.of()),
            new TradePaletteConfig(
                    TradePaletteRerollStrategy.PERSISTENT,
                    128,
                    0.75,
                    new TradeMemoryRecoveryConfig(24_000L, 0.01, 24_000L, 24_000L, 0L),
                    false,
                    Set.of()
            ),
            new MarketEconomyConfig(
                    MarketDemandConfig.DEFAULT,
                    MarketDemandPriceConfig.DEFAULT,
                    MarketDemandStockConfig.DISABLED
            )
    );

    public VillagerTradeConfig {
        Objects.requireNonNull(specializations, "specializations");
        Objects.requireNonNull(palette, "palette");
        Objects.requireNonNull(economy, "economy");
    }
}
