package org.waterflane.villager_potential.core.api;

import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.ProfessionCareerState;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.SpecializationId;
import org.waterflane.villager_potential.core.TradeKey;
import org.waterflane.villager_potential.core.TradePaletteRerollStrategy;
import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectionFormatTest {
    @Test
    void rendersInitializedStateIncludingPersistentPaletteAndSummaries() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        SpecializationId archivist = SpecializationId.parse("example:archivist");
        TradeKey paper = new TradeKey.Offer(
                new TradeKey.Item("minecraft:paper", 24),
                new TradeKey.Item("minecraft:emerald", 1)
        );
        VillagerPotentialState state = VillagerPotentialState.createDefault()
                .withAptitude(librarian, 1.25)
                .assignProfession(librarian, 100L)
                .withCareer(
                        librarian,
                        new ProfessionCareerState(4_000L, 0.75, 100L, 300L)
                                .withSpecialization(archivist)
                )
                .recordPresentedTrades(
                        librarian,
                        List.of(paper),
                        List.of(paper),
                        500L,
                        16,
                        TradePaletteRerollStrategy.PERSISTENT
                )
                .recordTradeUse(librarian, paper, 600L, 16)
                .recordTradePurchase(librarian, paper, 600L);
        UUID villagerId = UUID.fromString("00000000-0000-0000-0000-000000000123");

        String inspection = String.join("\n", InspectionFormat.format(
                villagerId,
                3,
                PotentialViews.snapshot(state)
        ));

        assertTrue(inspection.contains("uuid=" + villagerId));
        assertTrue(inspection.contains("schema_version=" + state.schemaVersion()));
        assertTrue(inspection.contains("aptitudes={minecraft:librarian=1.25}"));
        assertTrue(inspection.contains("profession_time=4000,skill=0.75"));
        assertTrue(inspection.contains("vanilla_profession_level=3"));
        assertTrue(inspection.contains("specialization=example:archivist"));
        assertTrue(inspection.contains("palette[minecraft:librarian]=["));
        assertTrue(inspection.contains(
                "trade_memory={minecraft:librarian={entries=1,seen=1,used=1}}"));
        assertTrue(inspection.contains("demand={minecraft:librarian={entries=1,score="));
        assertTrue(inspection.contains("purchases=1}"));
    }

    @Test
    void emptyViewsRenderStableNeutralPlaceholders() {
        UUID villagerId = UUID.fromString("00000000-0000-0000-0000-00000000ffff");

        String inspection = String.join("\n", InspectionFormat.format(
                villagerId,
                1,
                PotentialViews.snapshot(VillagerPotentialState.createDefault())
        ));

        assertTrue(inspection.contains("active_profession=none"));
        assertTrue(inspection.contains("aptitudes={}"));
        assertTrue(inspection.contains("careers=0"));
        assertTrue(inspection.contains("persistent_palettes=0"));
        assertTrue(inspection.contains("trade_memory={}"));
        assertTrue(inspection.contains("demand={}"));
    }
}
