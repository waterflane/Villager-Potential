package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeMemoryRecoveryTest {
    private static final TradeMemoryRecoveryConfig CONFIG = new TradeMemoryRecoveryConfig(
            100L,
            0.5,
            200L,
            300L,
            25L
    );
    private static final TradeHistory SEEN_AT_100 = TradeHistory.seenAt(100L);

    @Test
    void weightedMemoryPenaltyDecaysOverProfessionTime() {
        assertEquals(1.0, weight(TradePaletteRerollStrategy.WEIGHTED_MEMORY, 100L, false));
        assertEquals(2.5, weight(TradePaletteRerollStrategy.WEIGHTED_MEMORY, 150L, false));
        assertEquals(4.0, weight(TradePaletteRerollStrategy.WEIGHTED_MEMORY, 200L, false));
    }

    @Test
    void weightedMemoryRespectsMinimumCandidateWeight() {
        TradeMemoryRecoveryConfig minimumWeight = new TradeMemoryRecoveryConfig(
                100L,
                0.5,
                200L,
                300L,
                0L
        );

        assertEquals(0.5, TradeMemoryRecovery.candidateWeight(
                TradePaletteRerollStrategy.WEIGHTED_MEMORY,
                4.0,
                SEEN_AT_100,
                100L,
                0.0,
                minimumWeight,
                false,
                0L,
                false
        ));
    }

    @Test
    void configuredRareTradeRecoversEarly() {
        assertEquals(4.0, weight(TradePaletteRerollStrategy.WEIGHTED_MEMORY, 125L, true));
        assertEquals(0.0, weight(TradePaletteRerollStrategy.EXHAUST, 124L, true));
        assertEquals(4.0, weight(TradePaletteRerollStrategy.EXHAUST, 125L, true));
        assertEquals(0L, TradeMemoryRecovery.effectiveCyclicCount(
                SEEN_AT_100,
                125L,
                CONFIG,
                true
        ));
    }

    @Test
    void incompatibleOlderClockAnchorCannotMakePenaltyPermanent() {
        assertEquals(4.0, weight(TradePaletteRerollStrategy.WEIGHTED_MEMORY, 50L, false));
        assertEquals(4.0, weight(TradePaletteRerollStrategy.EXHAUST, 50L, false));
    }

    @Test
    void exhaustRecoversOnlyAfterItsConfiguredWindow() {
        assertEquals(0.0, weight(TradePaletteRerollStrategy.EXHAUST, 299L, false));
        assertEquals(4.0, weight(TradePaletteRerollStrategy.EXHAUST, 300L, false));
    }

    @Test
    void cyclicStartsNewCycleOnlyAfterAllCandidatesAreIdle() {
        assertFalse(TradeMemoryRecovery.shouldResetCycle(
                List.of(TradeHistory.seenAt(100L), TradeHistory.seenAt(200L)),
                499L,
                CONFIG
        ));
        assertTrue(TradeMemoryRecovery.shouldResetCycle(
                List.of(TradeHistory.seenAt(100L), TradeHistory.seenAt(200L)),
                500L,
                CONFIG
        ));

        TradeKey learned = new TradeKey.Fallback("learned");
        TradePaletteState palette = new TradePaletteState(
                List.of(learned),
                Map.of(learned, SEEN_AT_100)
        ).resetSeenCounts(Set.of(learned));
        assertEquals(List.of(learned), palette.activeTrades());
        assertEquals(0L, palette.offerHistory().get(learned).timesSeen());
    }

    @Test
    void persistentAndVanillaRemainNeutralAtAnyElapsedTime() {
        TradeKey learned = new TradeKey.Fallback("persistent-learned");
        TradePaletteState persistent = new TradePaletteState(
                List.of(learned),
                Map.of(learned, SEEN_AT_100)
        );
        for (TradePaletteRerollStrategy strategy : List.of(
                TradePaletteRerollStrategy.PERSISTENT,
                TradePaletteRerollStrategy.VANILLA
        )) {
            assertEquals(4.0, weight(strategy, Long.MAX_VALUE, true), strategy.name());
        }
        TradePaletteState recoveredHistory = persistent.resetSeenCounts(Set.of(learned));
        assertEquals(List.of(learned), recoveredHistory.activeTrades());
    }

    private static double weight(
            TradePaletteRerollStrategy strategy,
            long professionTime,
            boolean rareProtected
    ) {
        return TradeMemoryRecovery.candidateWeight(
                strategy,
                4.0,
                SEEN_AT_100,
                professionTime,
                0.25,
                CONFIG,
                rareProtected,
                0L,
                false
        );
    }
}
