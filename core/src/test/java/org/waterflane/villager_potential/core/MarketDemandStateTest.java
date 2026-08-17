package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketDemandStateTest {
    private static final MarketDemandConfig CONFIG = new MarketDemandConfig(
            -5.0,
            2.0,
            10.0,
            4.0,
            0.5
    );

    @Test
    void successfulPurchasesRaiseDemandUntilTheBound() {
        MarketDemandState demand = MarketDemandState.firstPurchaseAt(100L, CONFIG);
        for (int purchase = 1; purchase < 150; purchase++) {
            demand = demand.recordPurchase(100L, CONFIG);
        }

        assertEquals(CONFIG.maximum(), demand.demandScore());
        assertEquals(150L, demand.timesPurchased());
        assertEquals(100L, demand.lastPurchaseGameTime());
    }

    @Test
    void demandFallsTowardBaselineOverElapsedServerTime() {
        MarketDemandState demand = MarketDemandState.firstPurchaseAt(100L, CONFIG);

        assertEquals(6.0, demand.scoreAt(100L, CONFIG));
        assertEquals(4.0, demand.scoreAt(104L, CONFIG));
        assertEquals(CONFIG.baseline(), demand.scoreAt(1_000L, CONFIG));
    }

    @Test
    void demandNeverCrossesConfiguredBaselineOrBounds() {
        MarketDemandState aboveMaximum = new MarketDemandState(20.0, 1L, 100L);
        MarketDemandState belowMinimum = new MarketDemandState(-20.0, 1L, 100L);

        assertEquals(CONFIG.maximum(), aboveMaximum.scoreAt(100L, CONFIG));
        assertEquals(CONFIG.baseline(), aboveMaximum.scoreAt(1_000L, CONFIG));
        assertEquals(CONFIG.minimum(), belowMinimum.scoreAt(100L, CONFIG));
        assertEquals(CONFIG.baseline(), belowMinimum.scoreAt(1_000L, CONFIG));
    }

    @Test
    void noElapsedTimeLeavesDemandUnchanged() {
        MarketDemandState demand = new MarketDemandState(7.5, 3L, 100L);

        assertEquals(7.5, demand.scoreAt(100L, CONFIG));
        assertEquals(7.5, demand.scoreAt(99L, CONFIG));
    }

    @Test
    void lazyEvaluationIsDeterministicAndDoesNotMaterializeReads() {
        MarketDemandState demand = new MarketDemandState(8.0, 3L, 100L);

        double direct = demand.scoreAt(108L, CONFIG);
        demand.scoreAt(104L, CONFIG);
        double afterIntermediateRead = demand.scoreAt(108L, CONFIG);

        assertEquals(4.0, direct);
        assertEquals(direct, afterIntermediateRead);
        assertEquals(new MarketDemandState(8.0, 3L, 100L), demand);
    }

    @Test
    void purchaseAppliesElapsedDecayBeforeIncreasingDemand() {
        MarketDemandState demand = new MarketDemandState(8.0, 3L, 100L);

        MarketDemandState purchased = demand.recordPurchase(108L, CONFIG);

        assertEquals(8.0, purchased.demandScore());
        assertEquals(4L, purchased.timesPurchased());
        assertEquals(108L, purchased.lastPurchaseGameTime());
    }

    @Test
    void rejectsInvalidStateAndConfiguration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MarketDemandState(Double.NaN, 1L, 0L)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new MarketDemandConfig(1.0, 0.0, 2.0, 1.0, 0.1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new MarketDemandConfig(0.0, 1.0, 0.5, 1.0, 0.1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new MarketDemandConfig(0.0, 1.0, 2.0, 1.0, -0.1)
        );
    }
}
