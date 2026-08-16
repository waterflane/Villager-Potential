package org.waterflane.villager_potential;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.ProfessionCareerState;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VillagerTradeActivityTest {
    private static final ProfessionId LIBRARIAN = ProfessionId.parse("minecraft:librarian");

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void completedTradeEventRecordsCurrentProfessionActivityOnly() {
        ProfessionCareerState career = ProfessionCareerState.firstAssignedAt(20L)
                .withLearnedSkill(0.45);
        VillagerPotentialState initialState = new VillagerPotentialState(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                Map.of(LIBRARIAN, 1.25)
        ).assignProfession(LIBRARIAN, 20L).withCareer(LIBRARIAN, career);
        AtomicReference<VillagerPotentialState> state = new AtomicReference<>(initialState);
        Villager villager = mock(Villager.class);
        VillagerData villagerData = mock(VillagerData.class);
        ServerLevel level = mock(ServerLevel.class);
        TradeWithVillagerEvent event = mock(TradeWithVillagerEvent.class);
        MerchantOffer offer = mock(MerchantOffer.class);

        when(event.getAbstractVillager()).thenReturn(villager);
        when(event.getMerchantOffer()).thenReturn(offer);
        when(villager.level()).thenReturn(level);
        when(level.getGameTime()).thenReturn(100L);
        when(villager.getVillagerData()).thenReturn(villagerData);
        when(villagerData.getProfession()).thenReturn(VillagerProfession.LIBRARIAN);
        when(villager.getVillagerXp()).thenReturn(37);
        when(villager.getData(any(Supplier.class))).thenAnswer(ignored -> state.get());
        when(villager.setData(any(Supplier.class), any())).thenAnswer(invocation ->
                state.getAndSet(invocation.getArgument(1))
        );

        VillagerPotentialEvents.onTradeWithVillager(event);

        VillagerPotentialState updated = state.get();
        assertEquals(
                VillagerPotentialAttachments.PROFESSION_ACTIVITY_CONFIG.baseline()
                        + VillagerPotentialAttachments.PROFESSION_ACTIVITY_CONFIG.increasePerTrade(),
                updated.professionActivityFor(
                        LIBRARIAN,
                        100L,
                        VillagerPotentialAttachments.PROFESSION_ACTIVITY_CONFIG
                )
        );
        assertEquals(career, updated.careerFor(LIBRARIAN).orElseThrow());
        assertEquals(37, villager.getVillagerXp());
        verify(villager, never()).setVillagerXp(any(Integer.class));
        assertEquals(
                1L,
                updated.tradePaletteFor(LIBRARIAN).orElseThrow()
                        .offerHistory().get(MerchantOfferTradeKeys.from(offer)).timesUsed()
        );
    }
}
