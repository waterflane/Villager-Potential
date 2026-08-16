package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static TradeKey trade(String result) {
        return new TradeKey.Offer(
                new TradeKey.Item("minecraft:emerald", 1),
                new TradeKey.Item(result, 1)
        );
    }
}
