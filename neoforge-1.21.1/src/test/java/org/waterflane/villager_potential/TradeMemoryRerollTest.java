package org.waterflane.villager_potential;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.SpecializationBiasConfig;
import org.waterflane.villager_potential.core.TradeHistory;
import org.waterflane.villager_potential.core.TradeKey;
import org.waterflane.villager_potential.core.TradePaletteState;
import org.waterflane.villager_potential.core.TradePaletteRerollStrategy;
import org.waterflane.villager_potential.core.TradeSelectionResolver;
import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradeMemoryRerollTest {
    private static final ProfessionId LIBRARIAN = ProfessionId.parse("minecraft:librarian");
    private static final ProfessionId FARMER = ProfessionId.parse("minecraft:farmer");
    private static final double PENALTY_MULTIPLIER = 0.25;
    private static final SpecializationBiasConfig BIAS_CONFIG = new SpecializationBiasConfig(
            0.0,
            1.0,
            0.1,
            1.0,
            2.0
    );

    @Test
    void defaultsToPersistentWithANonBlacklistingMemoryPenalty() {
        assertEquals(
                TradePaletteRerollStrategy.PERSISTENT,
                Config.tradePaletteRerollStrategy()
        );
        assertTrue(Config.seenTradeWeightMultiplier() > 0.0);
        assertTrue(Config.seenTradeWeightMultiplier() < 1.0);
    }

    @Test
    void weightedMemoryRetainsMendingAndAppliesItsConfiguredPenalty() {
        TradeKey mending = MerchantOfferTradeKeys.from(bookOffer("mending"));
        TradeKey unbreaking = MerchantOfferTradeKeys.from(bookOffer("unbreaking"));
        TradePaletteState memory = TradePaletteState.empty()
                .recordPresented(
                        List.of(mending),
                        List.of(mending),
                        100L,
                        16,
                        TradePaletteRerollStrategy.WEIGHTED_MEMORY
                )
                .recordPresented(
                        List.of(unbreaking),
                        List.of(unbreaking),
                        100L,
                        16,
                        TradePaletteRerollStrategy.WEIGHTED_MEMORY
                );
        TradeSelectionResolver.Rules rules = new TradeSelectionResolver.Rules(
                0.0,
                BIAS_CONFIG,
                TradePaletteRerollStrategy.WEIGHTED_MEMORY,
                100L,
                Config.seenTradeWeightMultiplier(),
                Config.tradeMemoryRecoveryConfig(),
                0L,
                false
        );

        double mendingWeight = TradeSelectionResolver.resolvedWeight(
                new TradeSelectionResolver.Candidate(
                        1.0,
                        1.0,
                        1.0,
                        memory.offerHistory().get(mending),
                        Config.isRareTradeProtected(mending)
                ),
                rules
        );
        double unseenWeight = TradeSelectionResolver.resolvedWeight(
                new TradeSelectionResolver.Candidate(1.0, 1.0, 1.0, null, false),
                rules
        );

        assertTrue(memory.offerHistory().containsKey(mending));
        assertEquals(1L, memory.offerHistory().get(mending).timesSeen());
        assertEquals(
                Math.max(
                        Config.seenTradeWeightMultiplier(),
                        Config.tradeMemoryRecoveryConfig().minimumCandidateWeight()
                ),
                mendingWeight,
                0.000_000_1
        );
        assertEquals(1.0, unseenWeight);
        assertTrue(mendingWeight > 0.0, "the configured memory penalty is not a blacklist");
        assertTrue(mendingWeight < unseenWeight);
    }

    @Test
    void exhaustSelectsOnlyUnseenCandidates() {
        MerchantOffer seen = compassOffer();
        MerchantOffer unseen = bookOffer("unbreaking");
        TradeKey seenKey = MerchantOfferTradeKeys.from(seen);
        MerchantOffers offers = new MerchantOffers();

        SpecializedTradeSelection.addWeightedOffers(
                mock(Villager.class),
                offers,
                new VillagerTrades.ItemListing[]{
                        (entity, random) -> seen,
                        (entity, random) -> unseen
                },
                1,
                VillagerProfession.LIBRARIAN,
                1,
                Optional.empty(),
                0.0,
                BIAS_CONFIG,
                Map.of(seenKey, TradeHistory.seenAt(10L)),
                PENALTY_MULTIPLIER,
                TradePaletteRerollStrategy.EXHAUST,
                RandomSource.create(12L)
        );

        assertEquals(List.of(unseen), offers);
    }

    @Test
    void cyclicStartsNextCycleWithLeastSeenCandidates() {
        MerchantOffer compass = compassOffer();
        MerchantOffer book = bookOffer("unbreaking");
        TradeKey compassKey = MerchantOfferTradeKeys.from(compass);
        TradeKey bookKey = MerchantOfferTradeKeys.from(book);
        MerchantOffers offers = new MerchantOffers();

        SpecializedTradeSelection.addWeightedOffers(
                mock(Villager.class),
                offers,
                new VillagerTrades.ItemListing[]{
                        (entity, random) -> compass,
                        (entity, random) -> book
                },
                1,
                VillagerProfession.LIBRARIAN,
                1,
                Optional.empty(),
                0.0,
                BIAS_CONFIG,
                Map.of(
                        compassKey, new TradeHistory(2L, OptionalLong.empty(), 0L, OptionalLong.empty()),
                        bookKey, TradeHistory.seenAt(10L)
                ),
                PENALTY_MULTIPLIER,
                TradePaletteRerollStrategy.CYCLIC,
                RandomSource.create(12L)
        );

        assertEquals(List.of(book), offers);
    }

    @Test
    void vanillaModeIgnoresTradeMemory() {
        MerchantOffer seen = compassOffer();
        TradeKey seenKey = MerchantOfferTradeKeys.from(seen);
        MerchantOffers offers = new MerchantOffers();

        SpecializedTradeSelection.addWeightedOffers(
                mock(Villager.class),
                offers,
                new VillagerTrades.ItemListing[]{
                        (entity, random) -> seen,
                        (entity, random) -> bookOffer("unbreaking")
                },
                2,
                VillagerProfession.LIBRARIAN,
                1,
                Optional.empty(),
                0.0,
                BIAS_CONFIG,
                Map.of(seenKey, TradeHistory.seenAt(10L)),
                0.0,
                TradePaletteRerollStrategy.VANILLA,
                RandomSource.create(12L)
        );

        assertTrue(offers.contains(seen));
    }

    @Test
    void persistentInitialGenerationUsesTheResolver() {
        MerchantOffer compass = compassOffer();
        MerchantOffer book = bookOffer("unbreaking");
        VillagerTrades.ItemListing[] candidates = {
                (entity, random) -> compass,
                (entity, random) -> book
        };
        MerchantOffers expected = new MerchantOffers();
        MerchantOffers actual = new MerchantOffers();

        expected.add(candidates[RandomSource.create(93L).nextInt(candidates.length)]
                .getOffer(mock(Villager.class), RandomSource.create(1L)));
        SpecializedTradeSelection.addWeightedOffers(
                mock(Villager.class),
                actual,
                candidates,
                1,
                VillagerProfession.LIBRARIAN,
                1,
                Optional.empty(),
                0.0,
                BIAS_CONFIG,
                Map.of(),
                1.0,
                TradePaletteRerollStrategy.PERSISTENT,
                RandomSource.create(93L)
        );

        assertEquals(expected, actual);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void persistentMendingSurvivesReassignmentSaveLoadAndProfessionChanges() {
        MerchantOffer mending = bookOffer("mending");
        TradeKey mendingKey = MerchantOfferTradeKeys.from(mending);
        mending.setSpecialPriceDiff(-5);
        assertEquals(7, mending.getCostA().getCount());
        assertEquals(mendingKey, MerchantOfferTradeKeys.from(mending));

        VillagerPotentialState initial = new VillagerPotentialState(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                Map.of(LIBRARIAN, 1.0)
        ).assignProfession(LIBRARIAN, 100L);
        AtomicReference<VillagerPotentialState> state = new AtomicReference<>(initial);
        Villager villager = mock(Villager.class);
        VillagerData data = mock(VillagerData.class);
        when(villager.getVillagerData()).thenReturn(data);
        when(data.getProfession()).thenReturn(VillagerProfession.LIBRARIAN);
        when(villager.getData(any(Supplier.class))).thenAnswer(ignored -> state.get());
        when(villager.setData(any(Supplier.class), any())).thenAnswer(invocation ->
                state.getAndSet(invocation.getArgument(1))
        );

        MerchantOffers generated = new MerchantOffers();
        generated.add(mending);
        VillagerPotentialAttachments.recordGeneratedOffers(villager, generated, 0, 150L, 16);

        VillagerPotentialState exhausted = state.get();
        for (int use = 0; use < mending.getMaxUses(); use++) {
            exhausted = exhausted.recordTradeUse(LIBRARIAN, mendingKey, 151L + use, 16);
        }
        state.set(exhausted);

        TradePaletteState learned = state.get().tradePaletteFor(LIBRARIAN).orElseThrow();
        assertEquals(List.of(mendingKey), learned.activeTrades());
        assertEquals(mending.getMaxUses(), learned.offerHistory().get(mendingKey).timesUsed());
        assertEquals(mending.getMaxUses(), learned.usesSinceRestock().get(mendingKey));

        generated.clear();
        state.set(state.get().clearActiveProfession().assignProfession(LIBRARIAN, 200L));
        assertEquals(learned, state.get().tradePaletteFor(LIBRARIAN).orElseThrow());

        Tag saved = VillagerPotentialAttachments.CODEC
                .encodeStart(NbtOps.INSTANCE, state.get())
                .getOrThrow();
        VillagerPotentialState loaded = VillagerPotentialAttachments.CODEC
                .parse(NbtOps.INSTANCE, saved)
                .getOrThrow()
                .assignProfession(FARMER, 300L)
                .assignProfession(LIBRARIAN, 400L);
        state.set(loaded);
        assertEquals(learned, loaded.tradePaletteFor(LIBRARIAN).orElseThrow());

        VillagerTrades.ItemListing mendingListing = (entity, random) -> bookOffer("mending");
        AtomicInteger unrelatedCandidateCalls = new AtomicInteger();
        VillagerTrades.ItemListing unrelated = (entity, random) -> {
            unrelatedCandidateCalls.incrementAndGet();
            return bookOffer("unbreaking");
        };
        MerchantOffers restored = new MerchantOffers();

        SpecializedTradeSelection.restorePersistentOffers(
                villager,
                restored,
                learned.activeTrades(),
                learned.usesSinceRestock(),
                List.<VillagerTrades.ItemListing[]>of(
                        new VillagerTrades.ItemListing[]{mendingListing, unrelated}
                ),
                RandomSource.create(93L)
        );

        assertEquals(
                List.of(mendingKey),
                restored.stream().map(MerchantOfferTradeKeys::from).toList()
        );
        assertEquals(mending.getMaxUses(), restored.getFirst().getUses());
        assertTrue(restored.getFirst().isOutOfStock());
        assertEquals(0, unrelatedCandidateCalls.get());

        AtomicInteger generatedEvents = new AtomicInteger();
        try (var registration = VillagerPotentialTradeEvents.onPaletteEntriesGenerated(event ->
                generatedEvents.incrementAndGet()
        )) {
            VillagerPotentialAttachments.recordGeneratedOffers(
                    villager,
                    restored,
                    0,
                    450L,
                    16,
                    TradePaletteRerollStrategy.PERSISTENT
            );
        }
        assertEquals(0, generatedEvents.get());
        assertEquals(List.of(mendingKey), state.get().tradePaletteFor(LIBRARIAN)
                .orElseThrow().activeTrades());
    }

    @Test
    void persistentLevelUpSelectsOnlyNewlyUnlockedTrades() {
        MerchantOffer learned = compassOffer();
        MerchantOffer firstNew = bookOffer("unbreaking");
        MerchantOffer secondNew = bookOffer("mending");
        MerchantOffers offers = new MerchantOffers();
        offers.add(learned);

        SpecializedTradeSelection.addWeightedOffers(
                mock(Villager.class),
                offers,
                new VillagerTrades.ItemListing[]{
                        (entity, random) -> firstNew,
                        (entity, random) -> secondNew
                },
                2,
                VillagerProfession.LIBRARIAN,
                2,
                Optional.empty(),
                0.0,
                BIAS_CONFIG,
                Map.of(),
                1.0,
                TradePaletteRerollStrategy.PERSISTENT,
                RandomSource.create(93L)
        );

        assertEquals(3, offers.size());
        assertEquals(learned, offers.getFirst());
        assertTrue(offers.contains(firstNew));
        assertTrue(offers.contains(secondNew));
    }

    private static MerchantOffer compassOffer() {
        return new MerchantOffer(
                new ItemCost(Items.EMERALD, 4),
                Optional.empty(),
                new ItemStack(Items.COMPASS),
                12,
                5,
                0.05F
        );
    }

    private static MerchantOffer bookOffer(String enchantmentPath) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(enchantment(enchantmentPath), 1);
        book.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
        return new MerchantOffer(
                new ItemCost(Items.EMERALD, 12),
                Optional.of(new ItemCost(Items.BOOK)),
                book,
                12,
                5,
                0.05F
        );
    }

    @SuppressWarnings("unchecked")
    private static Holder<Enchantment> enchantment(String path) {
        Holder<Enchantment> enchantment = mock(Holder.class);
        ResourceKey<Enchantment> key = ResourceKey.create(
                Registries.ENCHANTMENT,
                ResourceLocation.fromNamespaceAndPath("test", path)
        );
        when(enchantment.unwrapKey()).thenReturn(Optional.of(key));
        return enchantment;
    }
}
