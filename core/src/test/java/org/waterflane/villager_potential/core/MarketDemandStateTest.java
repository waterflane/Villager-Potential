package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketDemandStateTest {
    @Test
    void successfulPurchasesRaiseDemandUntilTheBound() {
        MarketDemandState demand = MarketDemandState.firstPurchaseAt(100L);
        for (int purchase = 1; purchase < 150; purchase++) {
            demand = demand.recordPurchase(100L + purchase);
        }

        assertEquals(MarketDemandState.MAX_DEMAND_SCORE, demand.demandScore());
        assertEquals(150L, demand.timesPurchased());
        assertEquals(249L, demand.lastPurchaseGameTime());
    }

    @Test
    void rejectsScoresOutsideTheDemandBounds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MarketDemandState(MarketDemandState.MIN_DEMAND_SCORE - 1, 1L, 0L)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new MarketDemandState(MarketDemandState.MAX_DEMAND_SCORE + 1, 1L, 0L)
        );
    }
}
