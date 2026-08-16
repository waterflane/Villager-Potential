package org.waterflane.villager_potential;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.TradeKey;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MerchantOfferTradeKeysTest {
    @Test
    void equivalentOffersProduceTheSameKey() {
        MerchantOffer first = offer(
                new ItemCost(Items.EMERALD, 4),
                Optional.empty(),
                new ItemStack(Items.COMPASS)
        );
        MerchantOffer second = offer(
                new ItemCost(Items.EMERALD, 4),
                Optional.empty(),
                new ItemStack(Items.COMPASS)
        );

        assertEquals(MerchantOfferTradeKeys.from(first), MerchantOfferTradeKeys.from(second));
    }

    @Test
    void differentBookEnchantmentsAndLevelsProduceDifferentKeys() {
        TradeKey mendingOne = MerchantOfferTradeKeys.from(bookOffer("mending", 1));
        TradeKey unbreakingOne = MerchantOfferTradeKeys.from(bookOffer("unbreaking", 1));
        TradeKey unbreakingThree = MerchantOfferTradeKeys.from(bookOffer("unbreaking", 3));

        assertNotEquals(mendingOne, unbreakingOne);
        assertNotEquals(unbreakingOne, unbreakingThree);
    }

    @Test
    void temporaryPriceChangesDoNotChangeIdentity() {
        MerchantOffer offer = offer(
                new ItemCost(Items.EMERALD, 20),
                Optional.of(new ItemCost(Items.BOOK)),
                new ItemStack(Items.ENCHANTED_BOOK)
        );
        TradeKey original = MerchantOfferTradeKeys.from(offer);

        offer.setSpecialPriceDiff(-10);

        assertEquals(10, offer.getCostA().getCount());
        assertEquals(original, MerchantOfferTradeKeys.from(offer));
    }

    @Test
    void unreadableModdedOfferUsesAStableFallback() {
        MerchantOffer offer = mock(MerchantOffer.class);
        when(offer.getBaseCostA()).thenThrow(new IllegalStateException("custom offer"));

        TradeKey first = MerchantOfferTradeKeys.from(offer);
        TradeKey second = MerchantOfferTradeKeys.from(offer);

        assertEquals(new TradeKey.Fallback("merchant-offer:" + offer.getClass().getName()), first);
        assertEquals(first, second);
    }

    private static MerchantOffer bookOffer(String enchantmentPath, int level) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(enchantment(enchantmentPath), level);
        book.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
        return offer(
                new ItemCost(Items.EMERALD, 12),
                Optional.of(new ItemCost(Items.BOOK)),
                book
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

    private static MerchantOffer offer(
            ItemCost costA,
            Optional<ItemCost> costB,
            ItemStack result
    ) {
        return new MerchantOffer(costA, costB, result, 12, 5, 0.05F);
    }
}
