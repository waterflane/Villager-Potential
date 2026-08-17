package org.waterflane.villager_potential.core;

import java.util.Objects;

/** Pure demand-to-stock calculations shared by platform integrations. */
public final class MarketDemandStock {
    private MarketDemandStock() {
    }

    /**
     * Returns the effective use ceiling for the next vanilla-approved restock.
     * Demand at or below the normal baseline is neutral. Both the added uses
     * and the final ceiling are independently capped.
     */
    public static int maximumUses(
            int vanillaMaximumUses,
            double demandScore,
            MarketDemandConfig demandConfig,
            MarketDemandStockConfig stockConfig
    ) {
        if (vanillaMaximumUses < 1) {
            throw new IllegalArgumentException("vanillaMaximumUses must be positive");
        }
        if (!Double.isFinite(demandScore)) {
            throw new IllegalArgumentException("demandScore must be finite");
        }
        Objects.requireNonNull(demandConfig, "demandConfig");
        Objects.requireNonNull(stockConfig, "stockConfig");
        if (!stockConfig.enabled() || stockConfig.maximumAdditionalUses() == 0) {
            return vanillaMaximumUses;
        }

        int roomUnderAbsoluteCap = stockConfig.maximumUsesPerOffer() - vanillaMaximumUses;
        if (roomUnderAbsoluteCap <= 0 || demandScore <= demandConfig.baseline()) {
            return vanillaMaximumUses;
        }

        double demandRange = demandConfig.maximum() - demandConfig.baseline();
        if (demandRange <= 0.0) {
            return vanillaMaximumUses;
        }
        double demandFraction = Math.min(
                1.0,
                (demandScore - demandConfig.baseline()) / demandRange
        );
        int additionalUses = (int) Math.floor(
                demandFraction
                        * stockConfig.influenceStrength()
                        * stockConfig.maximumAdditionalUses()
        );
        additionalUses = Math.min(additionalUses, roomUnderAbsoluteCap);
        return vanillaMaximumUses + Math.max(0, additionalUses);
    }
}
