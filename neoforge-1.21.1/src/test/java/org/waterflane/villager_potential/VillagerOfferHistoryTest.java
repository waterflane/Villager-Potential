package org.waterflane.villager_potential;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.TradeHistory;
import org.waterflane.villager_potential.core.TradeKey;
import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.Map;
import java.util.OptionalLong;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VillagerOfferHistoryTest {
    private static final ProfessionId LIBRARIAN = ProfessionId.parse("minecraft:librarian");

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void generatedOfferIsRememberedBeforeAnyTradeCompletes() {
        VillagerPotentialState initial = new VillagerPotentialState(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                Map.of(LIBRARIAN, 1.0)
        );
        AtomicReference<VillagerPotentialState> state = new AtomicReference<>(initial);
        Villager villager = mock(Villager.class);
        VillagerData data = mock(VillagerData.class);
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 4),
                Optional.empty(),
                new ItemStack(Items.COMPASS),
                12,
                5,
                0.05F
        );
        MerchantOffers offers = new MerchantOffers();
        offers.add(offer);

        when(villager.getVillagerData()).thenReturn(data);
        when(data.getProfession()).thenReturn(VillagerProfession.LIBRARIAN);
        when(villager.getData(any(Supplier.class))).thenAnswer(ignored -> state.get());
        when(villager.setData(any(Supplier.class), any())).thenAnswer(invocation ->
                state.getAndSet(invocation.getArgument(1))
        );

        VillagerPotentialAttachments.recordGeneratedOffers(
                villager,
                offers,
                0,
                300L,
                16
        );

        TradeKey key = MerchantOfferTradeKeys.from(offer);
        TradeHistory history = state.get().tradePaletteFor(LIBRARIAN)
                .orElseThrow().offerHistory().get(key);
        assertEquals(1L, history.timesSeen());
        assertEquals(OptionalLong.of(300L), history.lastSeen());
        assertEquals(0L, history.timesUsed());
        assertEquals(java.util.List.of(key), state.get().tradePaletteFor(LIBRARIAN)
                .orElseThrow().activeTrades());
    }
}
