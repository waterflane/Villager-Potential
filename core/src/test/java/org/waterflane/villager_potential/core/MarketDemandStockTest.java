package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketDemandStockTest {
    private static final MarketDemandConfig DEMAND = new MarketDemandConfig(
            0.0,
            20.0,
            100.0,
            1.0,
            0.0
    );

    @Test
    void disabledModeMatchesVanillaMaximumUsesAtEveryDemand() {
        MarketDemandStockConfig disabled = new MarketDemandStockConfig(false, 64, 64);

        assertEquals(12, MarketDemandStock.maximumUses(12, 0.0, DEMAND, disabled));
        assertEquals(12, MarketDemandStock.maximumUses(12, 100.0, DEMAND, disabled));
    }

    @Test
    void increasedDemandChangesConfiguredStock() {
        MarketDemandStockConfig configured = new MarketDemandStockConfig(true, 8, 32);

        assertEquals(12, MarketDemandStock.maximumUses(12, 20.0, DEMAND, configured));
        assertEquals(16, MarketDemandStock.maximumUses(12, 60.0, DEMAND, configured));
        assertEquals(20, MarketDemandStock.maximumUses(12, 100.0, DEMAND, configured));
    }

    @Test
    void additionalAndAbsoluteCapsBothApply() {
        MarketDemandStockConfig additionalCap = new MarketDemandStockConfig(true, 3, 64);
        MarketDemandStockConfig absoluteCap = new MarketDemandStockConfig(true, 20, 16);

        assertEquals(15, MarketDemandStock.maximumUses(12, 1_000.0, DEMAND, additionalCap));
        assertEquals(16, MarketDemandStock.maximumUses(12, 1_000.0, DEMAND, absoluteCap));
        assertEquals(20, MarketDemandStock.maximumUses(20, 1_000.0, DEMAND, absoluteCap));
    }
}
