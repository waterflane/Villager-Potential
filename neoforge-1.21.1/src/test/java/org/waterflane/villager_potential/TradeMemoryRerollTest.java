package org.waterflane.villager_potential;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
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
import org.waterflane.villager_potential.core.SpecializationBiasConfig;
import org.waterflane.villager_potential.core.TradeHistory;
import org.waterflane.villager_potential.core.TradeKey;
import org.waterflane.villager_potential.core.TradePaletteState;
import org.waterflane.villager_potential.core.TradePaletteRerollStrategy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradeMemoryRerollTest {
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
    void randomizedMendingKeyIsRememberedButNotBlacklisted() {
        MerchantOffer mending = bookOffer("mending");
        MerchantOffer unbreaking = bookOffer("unbreaking");
        MerchantOffer compass = compassOffer();
        TradeKey mendingKey = MerchantOfferTradeKeys.from(mending);
        TradeKey unbreakingKey = MerchantOfferTradeKeys.from(unbreaking);
        Map<TradeKey, TradeHistory> history = TradePaletteState.empty()
                .recordPresented(List.of(mendingKey), List.of(mendingKey), 100L, 16)
                .offerHistory();
        VillagerTrades.ItemListing randomizedBook = (entity, random) ->
                random.nextBoolean() ? mending : unbreaking;
        VillagerTrades.ItemListing compassListing = (entity, random) -> compass;
        Villager villager = mock(Villager.class);
        RandomSource random = RandomSource.create(4589123L);
        int mendingSelections = 0;
        int unbreakingSelections = 0;

        for (int attempt = 0; attempt < 40_000; attempt++) {
            MerchantOffers offers = new MerchantOffers();
            SpecializedTradeSelection.addWeightedOffers(
                    villager,
                    offers,
                    new VillagerTrades.ItemListing[]{randomizedBook, compassListing},
                    1,
                    VillagerProfession.LIBRARIAN,
                    1,
                    Optional.empty(),
                    0.0,
                    BIAS_CONFIG,
                    history,
                    PENALTY_MULTIPLIER,
                    random
            );

            TradeKey selected = MerchantOfferTradeKeys.from(offers.getFirst());
            if (selected.equals(mendingKey)) {
                mendingSelections++;
            } else if (selected.equals(unbreakingKey)) {
                unbreakingSelections++;
            }
        }

        assertTrue(mendingSelections > 0, "Mending must remain possible");
        assertTrue(
                mendingSelections * 2 < unbreakingSelections,
                "mending=" + mendingSelections + ", unbreaking=" + unbreakingSelections
        );
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
    void persistentWorkstationReassignmentRestoresWithoutNewCandidateSelection() {
        List<TradeKey> learned = List.of(
                MerchantOfferTradeKeys.from(compassOffer())
        );
        VillagerTrades.ItemListing compass = (entity, random) -> compassOffer();
        AtomicInteger unrelatedCandidateCalls = new AtomicInteger();
        VillagerTrades.ItemListing unrelated = (entity, random) -> {
            unrelatedCandidateCalls.incrementAndGet();
            return bookOffer("unbreaking");
        };
        MerchantOffers restored = new MerchantOffers();

        SpecializedTradeSelection.restorePersistentOffers(
                mock(Villager.class),
                restored,
                learned,
                List.<VillagerTrades.ItemListing[]>of(
                        new VillagerTrades.ItemListing[]{compass, unrelated}
                ),
                RandomSource.create(93L)
        );

        assertEquals(learned, restored.stream().map(MerchantOfferTradeKeys::from).toList());
        assertEquals(0, unrelatedCandidateCalls.get());
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
