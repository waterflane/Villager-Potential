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
import org.waterflane.villager_potential.core.TradePaletteState;
import org.waterflane.villager_potential.core.TradePaletteRerollStrategy;
import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Test
    void levelUpHistoryInspectsOnlyNewlyAppendedOffers() {
        AtomicReference<VillagerPotentialState> state = new AtomicReference<>(
                new VillagerPotentialState(
                        VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                        Map.of(LIBRARIAN, 1.0)
                )
        );
        Villager villager = carrier(state);
        MerchantOffer existingOffer = mock(MerchantOffer.class);
        when(existingOffer.getBaseCostA()).thenThrow(new AssertionError(
                "existing offers must not be re-identified during level-up"
        ));
        MerchantOffer appendedOffer = offer();
        MerchantOffers offers = new MerchantOffers();
        offers.add(existingOffer);
        offers.add(appendedOffer);

        assertDoesNotThrow(() -> VillagerPotentialAttachments.recordGeneratedOffers(
                villager,
                offers,
                1,
                300L,
                16,
                TradePaletteRerollStrategy.PERSISTENT
        ));

        TradeKey appended = MerchantOfferTradeKeys.from(appendedOffer);
        assertEquals(1L, state.get().tradePaletteFor(LIBRARIAN).orElseThrow()
                .offerHistory().get(appended).timesSeen());
    }

    @Test
    void persistentRestorationSkipsOfferIdentificationAndPaletteRewrite() {
        MerchantOffer learnedOffer = offer();
        TradeKey learned = MerchantOfferTradeKeys.from(learnedOffer);
        VillagerPotentialState initial = new VillagerPotentialState(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                Map.of(LIBRARIAN, 1.0)
        ).withTradePalette(
                LIBRARIAN,
                new TradePaletteState(List.of(learned), Map.of())
        );
        AtomicReference<VillagerPotentialState> state = new AtomicReference<>(initial);
        Villager villager = carrier(state);
        MerchantOffer restoredOffer = mock(MerchantOffer.class);
        when(restoredOffer.getBaseCostA()).thenThrow(new AssertionError(
                "restored offers must not be re-identified"
        ));
        MerchantOffers offers = new MerchantOffers();
        offers.add(restoredOffer);

        assertDoesNotThrow(() -> VillagerPotentialAttachments.recordGeneratedOffers(
                villager,
                offers,
                0,
                400L,
                16,
                TradePaletteRerollStrategy.PERSISTENT
        ));

        assertEquals(initial, state.get());
        verify(villager, never()).setData(any(Supplier.class), any());
    }

    private static MerchantOffer offer() {
        return new MerchantOffer(
                new ItemCost(Items.EMERALD, 4),
                Optional.empty(),
                new ItemStack(Items.COMPASS),
                12,
                5,
                0.05F
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Villager carrier(AtomicReference<VillagerPotentialState> state) {
        Villager villager = mock(Villager.class);
        VillagerData data = mock(VillagerData.class);
        when(villager.getVillagerData()).thenReturn(data);
        when(data.getProfession()).thenReturn(VillagerProfession.LIBRARIAN);
        when(villager.getData(any(Supplier.class))).thenAnswer(ignored -> state.get());
        when(villager.setData(any(Supplier.class), any())).thenAnswer(invocation ->
                state.getAndSet(invocation.getArgument(1))
        );
        return villager;
    }
}
