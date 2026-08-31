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
    void disabledPriceInfluenceIsNeutralWhileDemandCanRemainEnabled() {
        MarketDemandPriceConfig disabled = new MarketDemandPriceConfig(false, 0.75, 1.5);

        assertEquals(1.0, MarketDemandPricing.multiplier(100.0, DEMAND, disabled));
        assertEquals(20, MarketDemandPricing.adjustedPrice(
                20, 20, 64, 100.0, DEMAND, disabled
        ));
        assertEquals(
                new MarketDemandPricing.OfferAdjustment(1, 8),
                MarketDemandPricing.adjustedOffer(
                        1, 1, 64, 8,
                        MarketDemandPricing.PaymentKind.EMERALD,
                        100.0, DEMAND, disabled
                )
        );
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
    void emeraldPaymentChangesOnlyProductWithinTenPercent() {
        assertEquals(
                new MarketDemandPricing.OfferAdjustment(1, 4),
                MarketDemandPricing.adjustedOffer(
                        1, 1, 64, 4,
                        MarketDemandPricing.PaymentKind.EMERALD,
                        DEMAND.maximum(), DEMAND, PRICE
                )
        );
        assertEquals(
                new MarketDemandPricing.OfferAdjustment(1, 8),
                MarketDemandPricing.adjustedOffer(
                        1, 1, 64, 8,
                        MarketDemandPricing.PaymentKind.EMERALD,
                        DEMAND.maximum(), DEMAND, PRICE
                )
        );
        assertEquals(
                new MarketDemandPricing.OfferAdjustment(1, 18),
                MarketDemandPricing.adjustedOffer(
                        1, 1, 64, 20,
                        MarketDemandPricing.PaymentKind.EMERALD,
                        DEMAND.baseline()
                                + MarketDemandPriceConfig.DEFAULT.demandScoreForMaximumPrice(),
                        DEMAND, MarketDemandPriceConfig.DEFAULT
                )
        );
    }

    @Test
    void itemPaymentChangesOnlyProductWithinTwelveAndAHalfPercent() {
        assertEquals(
                new MarketDemandPricing.OfferAdjustment(22, 1),
                MarketDemandPricing.adjustedOffer(
                        20, 20, 64, 1,
                        MarketDemandPricing.PaymentKind.OTHER_ITEM,
                        DEMAND.maximum(), DEMAND, PRICE
                )
        );
        assertEquals(
                new MarketDemandPricing.OfferAdjustment(4, 1),
                MarketDemandPricing.adjustedOffer(
                        4, 4, 64, 1,
                        MarketDemandPricing.PaymentKind.OTHER_ITEM,
                        DEMAND.maximum(), DEMAND, PRICE
                )
        );
        assertEquals(
                new MarketDemandPricing.OfferAdjustment(22, 1),
                MarketDemandPricing.adjustedOffer(
                        25, 20, 64, 1,
                        MarketDemandPricing.PaymentKind.OTHER_ITEM,
                        DEMAND.maximum(), DEMAND, PRICE
                )
        );
    }

    @Test
    void totalOfferValuesStayInsideAbsoluteBasePercentageBounds() {
        assertEquals(
                new MarketDemandPricing.OfferAdjustment(20, 1),
                MarketDemandPricing.adjustedOffer(
                        40, 20, 64, 1,
                        MarketDemandPricing.PaymentKind.OTHER_ITEM,
                        DEMAND.baseline(), DEMAND, MarketDemandPriceConfig.DEFAULT
                )
        );
        assertEquals(
                22,
                MarketDemandPricing.maximumItemPaymentPrice(
                        20, 64, MarketDemandPriceConfig.DEFAULT
                )
        );
        assertEquals(
                new MarketDemandPricing.OfferAdjustment(1, 18),
                MarketDemandPricing.adjustedOffer(
                        7, 1, 64, 20,
                        MarketDemandPricing.PaymentKind.EMERALD,
                        DEMAND.maximum(), DEMAND, MarketDemandPriceConfig.DEFAULT
                )
        );
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
        assertThrows(
                IllegalArgumentException.class,
                () -> MarketDemandPricing.adjustedOffer(
                        1, 1, 64, 0,
                        MarketDemandPricing.PaymentKind.EMERALD,
                        20.0, DEMAND, PRICE
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new MarketDemandPriceConfig(true, 1.0, 2.0, 1.01, 0.20)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new MarketDemandPriceConfig(true, 1.0, 2.0, 0.15, -0.01)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new MarketDemandPriceConfig(
                        true, 1.0, 2.0, 0.15, 0.20, 0.0
                )
        );
    }
}
