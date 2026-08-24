package org.waterflane.villager_potential;

import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.AptitudeProvisioning;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.ProfessionSpecializationAssignment;
import org.waterflane.villager_potential.core.SpecializationId;
import org.waterflane.villager_potential.core.SpecializationBiasConfig;
import org.waterflane.villager_potential.core.TradeKey;
import org.waterflane.villager_potential.core.TradePaletteRerollStrategy;
import org.waterflane.villager_potential.core.VillagerPotentialState;
import org.waterflane.villager_potential.core.VillagerPotentialConfig;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExternalContentCompatibilityTest {
    private static final ProfessionId MODDED_PROFESSION =
            ProfessionId.parse("example_mod:alchemist");

    @Test
    void syntheticModdedProfessionIdAndStateSurvivePersistence() {
        assertEquals(
                MODDED_PROFESSION,
                VillagerProfessionIds.fromRegistryName(
                        ResourceLocation.fromNamespaceAndPath("example_mod", "alchemist")
                )
        );
        VillagerPotentialState state = AptitudeProvisioning.ensure(
                VillagerPotentialState.createDefault(),
                MODDED_PROFESSION,
                VillagerPotentialConfig.DEFAULT.aptitude(),
                new Random(42L)
        );
        state = ProfessionSpecializationAssignment.enterProfession(
                state,
                MODDED_PROFESSION,
                100L,
                Optional.empty(),
                new Random(7L)
        ).progressActiveProfession(40L, VillagerPotentialAttachments.SKILL_PROGRESSION_CONFIG);

        VillagerPotentialState restored = VillagerPotentialAttachments.CODEC.parse(
                NbtOps.INSTANCE,
                VillagerPotentialAttachments.CODEC.encodeStart(NbtOps.INSTANCE, state)
                        .getOrThrow()
        ).getOrThrow();

        assertEquals(state, restored);
        assertTrue(restored.aptitudeFor(MODDED_PROFESSION).isPresent());
        assertEquals(40L, restored.careerFor(MODDED_PROFESSION).orElseThrow()
                .accumulatedProfessionTime());
        assertEquals(SpecializationId.GENERAL,
                restored.specializationFor(MODDED_PROFESSION).orElseThrow());
    }

    @Test
    void syntheticForeignTradeIsGeneralAndKeepsStableLogicalIdentity() {
        MerchantOffer foreignOffer = stableForeignOffer();
        VillagerTrades.ItemListing foreignListing = (entity, random) -> foreignOffer;
        MerchantOfferTradeKeys.Identity identity = MerchantOfferTradeKeys.identify(foreignOffer);
        MerchantOffers selected = new MerchantOffers();

        SpecializedTradeSelection.addWeightedOffers(
                mock(Villager.class),
                selected,
                new VillagerTrades.ItemListing[]{foreignListing},
                1,
                VillagerProfession.LIBRARIAN,
                1,
                Optional.empty(),
                0.0,
                new SpecializationBiasConfig(0.0, 1.0, 0.0, 1.0, 1.0),
                Map.of(),
                1.0,
                TradePaletteRerollStrategy.PERSISTENT,
                RandomSource.create(1L)
        );

        assertEquals(VanillaTradeClassifications.GENERAL,
                VanillaTradeClassifications.classify(
                        VillagerProfession.LIBRARIAN,
                        1,
                        foreignListing
                ));
        assertTrue(identity.stable());
        assertTrue(identity.key() instanceof TradeKey.Offer);
        assertEquals(List.of(foreignOffer), selected);

        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        AtomicReference<VillagerPotentialState> state = new AtomicReference<>(
                new VillagerPotentialState(
                        VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                        Map.of(librarian, 1.0)
                ).assignProfession(librarian, 0L)
        );
        Villager carrier = carrier(state);
        VillagerPotentialAttachments.recordGeneratedOffers(
                carrier,
                selected,
                0,
                100L,
                16,
                TradePaletteRerollStrategy.PERSISTENT
        );
        VillagerPotentialAttachments.recordTrade(carrier, foreignOffer, 101L, 16);
        assertEquals(List.of(identity.key()), state.get().tradePaletteFor(librarian)
                .orElseThrow().activeTrades());
        assertTrue(state.get().marketDemandFor(librarian, identity.key()).isPresent());
    }

    @Test
    void unreadableForeignOfferUsesPreserveOnlyFallbackWithoutMemoryOrDemand() {
        MerchantOffer unreadable = mock(MerchantOffer.class);
        when(unreadable.getBaseCostA()).thenThrow(new IllegalStateException("foreign offer"));
        MerchantOfferTradeKeys.Identity identity = MerchantOfferTradeKeys.identify(unreadable);
        VillagerPotentialState initial = new VillagerPotentialState(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                Map.of(ProfessionId.parse("minecraft:librarian"), 1.0)
        ).assignProfession(ProfessionId.parse("minecraft:librarian"), 0L);
        AtomicReference<VillagerPotentialState> state = new AtomicReference<>(initial);
        Villager villager = carrier(state);
        MerchantOffers offers = new MerchantOffers();
        offers.add(unreadable);

        VillagerPotentialAttachments.recordGeneratedOffers(
                villager,
                offers,
                0,
                100L,
                16,
                TradePaletteRerollStrategy.PERSISTENT
        );
        VillagerPotentialAttachments.recordTrade(villager, unreadable, 101L, 16);

        assertFalse(identity.stable());
        assertTrue(identity.key() instanceof TradeKey.Fallback);
        assertSame(unreadable, offers.getFirst());
        assertTrue(state.get().tradePaletteFor(
                ProfessionId.parse("minecraft:librarian")
        ).orElseThrow().activeTrades().isEmpty());
        assertTrue(state.get().marketDemand().isEmpty());
    }

    @Test
    void unsupportedPersistentFallbackYieldsToOriginalTradeSystem() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        TradeKey unsupported = new TradeKey.Fallback("merchant-offer:foreign.CustomOffer");
        VillagerPotentialState initial = new VillagerPotentialState(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                Map.of(librarian, 1.0)
        ).assignProfession(librarian, 0L).recordPresentedTrades(
                librarian,
                List.of(unsupported),
                List.of(unsupported),
                10L,
                16,
                TradePaletteRerollStrategy.PERSISTENT
        );
        AtomicReference<VillagerPotentialState> state = new AtomicReference<>(initial);
        Villager villager = carrier(state);
        when(villager.getRandom()).thenReturn(RandomSource.create(1L));
        MerchantOffers offers = new MerchantOffers();

        boolean handled = SpecializedTradeSelection.tryAddOffers(
                villager,
                offers,
                new VillagerTrades.ItemListing[]{(entity, random) -> stableForeignOffer()},
                1
        );

        assertFalse(handled);
        assertTrue(offers.isEmpty());
        assertEquals(List.of(unsupported), state.get().tradePaletteFor(librarian)
                .orElseThrow().activeTrades());
    }

    @Test
    void persistentPaletteRestoresSupportedForeignListing() {
        MerchantOffer learnedOffer = stableForeignOffer();
        TradeKey learned = MerchantOfferTradeKeys.from(learnedOffer);
        VillagerTrades.ItemListing foreignListing = (entity, random) -> stableForeignOffer();
        MerchantOffers restored = new MerchantOffers();

        SpecializedTradeSelection.restorePersistentOffers(
                mock(Villager.class),
                restored,
                List.of(learned),
                List.<VillagerTrades.ItemListing[]>of(
                        new VillagerTrades.ItemListing[]{foreignListing}
                ),
                RandomSource.create(3L)
        );

        assertEquals(List.of(learned), restored.stream()
                .map(MerchantOfferTradeKeys::from)
                .toList());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Villager carrier(AtomicReference<VillagerPotentialState> state) {
        Villager villager = mock(Villager.class);
        var data = mock(net.minecraft.world.entity.npc.VillagerData.class);
        when(villager.getVillagerData()).thenReturn(data);
        when(data.getProfession()).thenReturn(VillagerProfession.LIBRARIAN);
        when(villager.getData(any(Supplier.class))).thenAnswer(ignored -> state.get());
        when(villager.setData(any(Supplier.class), any())).thenAnswer(invocation ->
                state.getAndSet(invocation.getArgument(1))
        );
        return villager;
    }

    private static MerchantOffer stableForeignOffer() {
        return new MerchantOffer(
                new ItemCost(Items.EMERALD, 3),
                Optional.empty(),
                new ItemStack(Items.AMETHYST_SHARD, 2),
                12,
                5,
                0.05F
        );
    }
}
