package org.waterflane.villager_potential;

import net.minecraft.world.entity.npc.Villager;
import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.TradeCategoryId;
import org.waterflane.villager_potential.core.TradePaletteRerollStrategy;
import org.waterflane.villager_potential.core.TradeKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class VillagerPotentialTradeEventsTest {
    private static final VillagerPotentialTradeEvents.CandidateWeight CONTEXT =
            new VillagerPotentialTradeEvents.CandidateWeight(
                    mock(Villager.class),
                    ProfessionId.parse("minecraft:librarian"),
                    2,
                    new TradeKey.Fallback("test:candidate"),
                    TradeCategoryId.parse("villager_potential:general"),
                    TradePaletteRerollStrategy.WEIGHTED_MEMORY
            );

    @Test
    void noListenerLeavesCandidateWeightUnchanged() {
        assertEquals(2.5, VillagerPotentialTradeEvents.modifyCandidateWeight(CONTEXT, 2.5));
    }

    @Test
    void listenersComposeAndUnsafeValuesAreIgnored() {
        try (var first = VillagerPotentialTradeEvents.onCandidateWeight(
                (context, weight) -> weight * 2.0
        ); var unsafe = VillagerPotentialTradeEvents.onCandidateWeight(
                (context, weight) -> -1.0
        )) {
            assertEquals(5.0,
                    VillagerPotentialTradeEvents.modifyCandidateWeight(CONTEXT, 2.5));
        }
    }
}
