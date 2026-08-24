package org.waterflane.villager_potential.core.api;

import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.ProfessionCareerState;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.SpecializationId;
import org.waterflane.villager_potential.core.TradeKey;
import org.waterflane.villager_potential.core.TradePaletteRerollStrategy;
import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PotentialViewsTest {
    private static final ProfessionId LIBRARIAN = ProfessionId.parse("minecraft:librarian");
    private static final SpecializationId ARCHIVIST =
            SpecializationId.parse("example:archivist");
    private static final TradeKey PAPER = new TradeKey.Offer(
            new TradeKey.Item("minecraft:paper", 24),
            new TradeKey.Item("minecraft:emerald", 1)
    );

    @Test
    void queriesAllSupportedPotentialDataThroughPortableIds() {
        PotentialView view = PotentialViews.snapshot(state());

        assertEquals(1.25, view.aptitude(LIBRARIAN).orElseThrow());
        assertEquals(0.75, view.skill(LIBRARIAN).orElseThrow());
        assertEquals(4_000L, view.career(LIBRARIAN).orElseThrow()
                .accumulatedProfessionTime());
        assertEquals(ARCHIVIST, view.specialization(LIBRARIAN).orElseThrow());
        assertEquals(List.of(PAPER), view.learnedTradePalette(LIBRARIAN));
        assertEquals(1L, view.tradeMemory(LIBRARIAN).get(PAPER).timesSeen());
        assertEquals(1L, view.tradeMemory(LIBRARIAN).get(PAPER).timesUsed());
        assertEquals(1L, view.demand(LIBRARIAN, PAPER).orElseThrow().timesPurchased());
    }

    @Test
    void returnedViewsAreDeeplyImmutableSnapshots() {
        VillagerPotentialState state = state();
        PotentialView view = PotentialViews.snapshot(state);

        assertThrows(UnsupportedOperationException.class,
                () -> view.aptitudes().put(LIBRARIAN, 9.0));
        assertThrows(UnsupportedOperationException.class,
                () -> view.careers().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> view.learnedTradePalette(LIBRARIAN).add(PAPER));
        assertThrows(UnsupportedOperationException.class,
                () -> view.tradeMemory(LIBRARIAN).clear());
        assertThrows(UnsupportedOperationException.class,
                () -> view.demand().get(LIBRARIAN).clear());

        state = state.withAptitude(LIBRARIAN, 2.0);
        assertEquals(1.25, view.aptitude(LIBRARIAN).orElseThrow());
        assertEquals(2.0, state.aptitudeFor(LIBRARIAN).orElseThrow());
    }

    private static VillagerPotentialState state() {
        ProfessionCareerState career = new ProfessionCareerState(
                4_000L,
                0.75,
                100L,
                300L
        ).withSpecialization(ARCHIVIST);
        return VillagerPotentialState.createDefault()
                .withAptitude(LIBRARIAN, 1.25)
                .assignProfession(LIBRARIAN, 100L)
                .withCareer(LIBRARIAN, career)
                .recordPresentedTrades(
                        LIBRARIAN,
                        List.of(PAPER),
                        List.of(PAPER),
                        500L,
                        16,
                        TradePaletteRerollStrategy.PERSISTENT
                )
                .recordTradeUse(LIBRARIAN, PAPER, 600L, 16)
                .recordTradePurchase(LIBRARIAN, PAPER, 600L);
    }
}
