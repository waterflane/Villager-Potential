package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketDemandPricingTest {
    private static final MarketDemandConfig DEMAND = new MarketDemandConfig(
            0.0,
            20.0,
            100.0,
            10.0,
            1.0
    );
    private static final MarketDemandPriceConfig PRICE =
            new MarketDemandPriceConfig(0.75, 1.5);

    @Test
    void normalDemandIsNeutral() {
        assertEquals(1.0, MarketDemandPricing.multiplier(20.0, DEMAND, PRICE));
        assertEquals(20, MarketDemandPricing.adjustedPrice(
                20, 20, 64, 20.0, DEMAND, PRICE
        ));
    }

    @Test
    void highDemandIncreasesPriceWithinConfiguredAndItemCaps() {
        assertEquals(0.75, MarketDemandPricing.multiplier(0.0, DEMAND, PRICE));
        assertEquals(1.5, MarketDemandPricing.multiplier(100.0, DEMAND, PRICE));
        assertEquals(15, MarketDemandPricing.adjustedPrice(
                20, 20, 64, 0.0, DEMAND, PRICE
        ));
        assertEquals(30, MarketDemandPricing.adjustedPrice(
                20, 20, 64, 100.0, DEMAND, PRICE
        ));
        assertEquals(64, MarketDemandPricing.adjustedPrice(
                60, 60, 64, 100.0, DEMAND, PRICE
        ));
    }

    @Test
    void vanillaDiscountRemainsPartOfTheAdjustedPrice() {
        assertEquals(25, MarketDemandPricing.adjustedPrice(
                15, 20, 64, 100.0, DEMAND, PRICE
        ));
    }

    @Test
    void decayedDemandLowersOnlyTheModAddedAmount() {
        MarketDemandState recent = new MarketDemandState(100.0, 8L, 100L);

        int recentPrice = MarketDemandPricing.adjustedPrice(
                20, 20, 64, recent.scoreAt(100L, DEMAND), DEMAND, PRICE
        );
        int decayedPrice = MarketDemandPricing.adjustedPrice(
                20, 20, 64, recent.scoreAt(140L, DEMAND), DEMAND, PRICE
        );

        assertEquals(30, recentPrice);
        assertEquals(25, decayedPrice);
    }

    @Test
    void rejectsInvalidMultiplierBoundsAndItemLimits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MarketDemandPriceConfig(0.0, 2.0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new MarketDemandPriceConfig(1.1, 2.0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new MarketDemandPriceConfig(1.0, 0.9)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> MarketDemandPricing.adjustedPrice(
                        1, 1, 0, 20.0, DEMAND, PRICE
                )
        );
    }
}
