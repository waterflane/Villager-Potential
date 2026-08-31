package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradePaletteStateTest {
    private static final TradeKey PAPER = trade("minecraft:paper");
    private static final TradeKey BOOK = trade("minecraft:book");
    private static final TradeKey COMPASS = trade("minecraft:compass");

    @Test
    void generatedOfferEntersMemoryBeforeUse() {
        TradePaletteState state = TradePaletteState.empty().recordPresented(
                List.of(PAPER),
                List.of(PAPER),
                100L,
                16
        );

        TradeHistory history = state.offerHistory().get(PAPER);
        assertEquals(1L, history.timesSeen());
        assertEquals(OptionalLong.of(100L), history.lastSeen());
        assertEquals(0L, history.timesUsed());
        assertTrue(history.lastUsed().isEmpty());
    }

    @Test
    void repeatedOfferUpdatesOnlyItsAggregateHistory() {
        TradePaletteState state = TradePaletteState.empty()
                .recordPresented(List.of(PAPER), List.of(PAPER), 100L, 16)
                .recordPresented(List.of(PAPER, BOOK), List.of(PAPER, BOOK), 140L, 16)
                .recordUsed(PAPER, 160L, 16);

        TradeHistory paper = state.offerHistory().get(PAPER);
        assertEquals(2L, paper.timesSeen());
        assertEquals(OptionalLong.of(140L), paper.lastSeen());
        assertEquals(1L, paper.timesUsed());
        assertEquals(OptionalLong.of(160L), paper.lastUsed());
        assertEquals(1L, state.offerHistory().get(BOOK).timesSeen());
    }

    @Test
    void currentStockUsageSurvivesPaletteRecreationUntilSleepRestock() {
        TradePaletteState used = TradePaletteState.empty()
                .recordPresented(List.of(PAPER), List.of(PAPER), 100L, 16)
                .recordUsed(PAPER, 120L, 16)
                .recordUsed(PAPER, 140L, 16);

        TradePaletteState recreated = new TradePaletteState(
                used.activeTrades(),
                used.offerHistory(),
                used.usesSinceRestock()
        );
        assertEquals(Map.of(PAPER, 2), recreated.usesSinceRestock());

        TradePaletteState restocked = recreated.resetRestockUses();
        assertTrue(restocked.usesSinceRestock().isEmpty());
        assertEquals(recreated.activeTrades(), restocked.activeTrades());
        assertEquals(recreated.offerHistory(), restocked.offerHistory());
        assertSame(restocked, restocked.resetRestockUses());
    }

    @Test
    void maximumEntriesPrunesLeastRecentlyObservedTrade() {
        TradePaletteState state = TradePaletteState.empty()
                .recordPresented(List.of(PAPER), List.of(PAPER), 100L, 2)
                .recordPresented(List.of(PAPER, BOOK), List.of(BOOK), 110L, 2)
                .recordPresented(List.of(BOOK, COMPASS), List.of(COMPASS), 120L, 2);

        assertEquals(2, state.offerHistory().size());
        assertFalse(state.offerHistory().containsKey(PAPER));
        assertTrue(state.offerHistory().containsKey(BOOK));
        assertTrue(state.offerHistory().containsKey(COMPASS));
    }

    @Test
    void persistentLevelUpAppendsWithoutReplacingLearnedTrades() {
        TradePaletteState state = TradePaletteState.empty()
                .recordPresented(List.of(PAPER, BOOK), List.of(PAPER, BOOK), 100L, 16)
                .recordPresented(
                        List.of(PAPER, BOOK, COMPASS),
                        List.of(COMPASS),
                        200L,
                        16
                );

        assertEquals(List.of(PAPER, BOOK, COMPASS), state.activeTrades());
    }

    @Test
    void persistentPaletteUsesTheSameBoundAsAggregateHistory() {
        TradePaletteState state = TradePaletteState.empty().recordPresented(
                List.of(PAPER, BOOK, COMPASS),
                List.of(PAPER, BOOK, COMPASS),
                100L,
                2
        );

        assertEquals(List.of(PAPER, BOOK), state.activeTrades());
        assertEquals(2, state.offerHistory().size());
    }

    @Test
    void observingNoNewOffersReusesTheUnchangedPalette() {
        TradePaletteState learned = TradePaletteState.empty().recordPresented(
                List.of(PAPER),
                List.of(PAPER),
                100L,
                16
        );

        for (TradePaletteRerollStrategy strategy : TradePaletteRerollStrategy.values()) {
            assertSame(
                    learned,
                    learned.recordPresented(
                            List.of(PAPER),
                            List.of(),
                            200L,
                            16,
                            strategy
                    ),
                    strategy.name()
            );
        }
    }

    @Test
    void nonPersistentModesDoNotCorruptLearnedPalette() {
        TradePaletteState learned = TradePaletteState.empty().recordPresented(
                List.of(PAPER),
                List.of(PAPER),
                100L,
                16
        );

        for (TradePaletteRerollStrategy strategy : TradePaletteRerollStrategy.values()) {
            if (strategy == TradePaletteRerollStrategy.PERSISTENT) {
                continue;
            }
            TradePaletteState rerolled = learned.recordPresented(
                    List.of(BOOK),
                    List.of(BOOK),
                    200L,
                    16,
                    strategy
            );
            assertEquals(List.of(PAPER), rerolled.activeTrades(), strategy.name());
            assertTrue(rerolled.offerHistory().containsKey(BOOK), strategy.name());
        }
    }

    @Test
    void switchingAwayFromPersistentAndBackKeepsLearnedTradesUsable() {
        TradePaletteState learned = TradePaletteState.empty().recordPresented(
                List.of(PAPER),
                List.of(PAPER),
                100L,
                16,
                TradePaletteRerollStrategy.PERSISTENT
        );
        TradePaletteState rerollMode = learned.recordPresented(
                List.of(BOOK),
                List.of(BOOK),
                200L,
                16,
                TradePaletteRerollStrategy.EXHAUST
        );
        TradePaletteState persistentAgain = rerollMode.recordPresented(
                List.of(PAPER, COMPASS),
                List.of(COMPASS),
                300L,
                16,
                TradePaletteRerollStrategy.PERSISTENT
        );

        assertEquals(List.of(PAPER), rerollMode.activeTrades());
        assertEquals(List.of(PAPER, COMPASS), persistentAgain.activeTrades());
        assertTrue(persistentAgain.offerHistory().containsKey(BOOK));
    }

    @Test
    void latestSeenTimeReportsTheNewestObservationOrZero() {
        assertEquals(0L, TradePaletteState.latestSeenTime(java.util.Map.of()));

        TradePaletteState palette = TradePaletteState.empty().recordPresented(
                List.of(),
                List.of(PAPER, BOOK),
                500L,
                16
        );
        assertEquals(
                500L,
                TradePaletteState.latestSeenTime(palette.offerHistory())
        );

        TradePaletteState updated = palette.recordPresented(
                List.of(),
                List.of(COMPASS),
                900L,
                16
        );
        assertEquals(
                900L,
                TradePaletteState.latestSeenTime(updated.offerHistory())
        );
    }

    private static TradeKey trade(String result) {
        return new TradeKey.Offer(
                new TradeKey.Item("minecraft:emerald", 1),
                new TradeKey.Item(result, 1)
        );
    }
}
