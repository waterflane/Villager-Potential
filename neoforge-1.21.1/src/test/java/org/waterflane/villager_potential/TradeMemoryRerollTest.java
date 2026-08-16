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

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    void defaultPenaltyDoesNotBlacklistSeenTrades() {
        assertTrue(Config.seenTradeWeightMultiplier() > 0.0);
        assertTrue(Config.seenTradeWeightMultiplier() < 1.0);
    }

    @Test
    void seenTradeWeightDecreases() {
        TradeKey seen = MerchantOfferTradeKeys.from(compassOffer());

        assertEquals(
                1.0,
                SpecializedTradeSelection.tradeMemoryWeight(
                        4.0,
                        seen,
                        Map.of(seen, TradeHistory.seenAt(10L)),
                        PENALTY_MULTIPLIER
                )
        );
    }

    @Test
    void unseenTradeWeightStaysUnchanged() {
        TradeKey seen = MerchantOfferTradeKeys.from(compassOffer());
        TradeKey unseen = MerchantOfferTradeKeys.from(bookOffer("unbreaking"));

        assertEquals(
                4.0,
                SpecializedTradeSelection.tradeMemoryWeight(
                        4.0,
                        unseen,
                        Map.of(seen, TradeHistory.seenAt(10L)),
                        PENALTY_MULTIPLIER
                )
        );
    }

    @Test
    void noHistoryKeepsTheSpecializationWeightedBaseline() {
        TradeKey candidate = MerchantOfferTradeKeys.from(compassOffer());

        assertEquals(
                4.0,
                SpecializedTradeSelection.tradeMemoryWeight(
                        4.0,
                        candidate,
                        Map.of(),
                        PENALTY_MULTIPLIER
                )
        );
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
