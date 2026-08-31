package org.waterflane.villager_potential;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.MarketDemandConfig;
import org.waterflane.villager_potential.core.MarketDemandPriceConfig;
import org.waterflane.villager_potential.core.MarketDemandState;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerDemandPricingTest {
    private static final MarketDemandConfig DEMAND = new MarketDemandConfig(
            0.0,
            0.0,
            100.0,
            1.0,
            1.0
    );
    private static final MarketDemandPriceConfig PRICE =
            new MarketDemandPriceConfig(1.0, 2.0);

    @Test
    void normalDemandGivesNeutralPrice() {
        MerchantOffer offer = offer(20);

        VillagerDemandPricing.apply(offer, DEMAND.baseline(), DEMAND, PRICE);

        assertEquals(20, offer.getCostA().getCount());
    }

    @Test
    void emeraldPaymentCountNeverChanges() {
        MerchantOffer offer = offer(20);

        VillagerDemandPricing.apply(offer, DEMAND.maximum(), DEMAND, PRICE);

        assertEquals(20, offer.getCostA().getCount());
        assertEquals(1, offer.getResult().getCount());
    }

    @Test
    void emeraldPaymentIsFixedToItsBaseCountWhilePricingIsEnabled() {
        MerchantOffer offer = offer(20, 0.5F, 1);
        offer.setSpecialPriceDiff(-5);
        assertEquals(25, offer.getCostA().getCount());

        VillagerDemandPricing.apply(offer, DEMAND.maximum(), DEMAND, PRICE);

        assertEquals(20, offer.getCostA().getCount());
    }

    @Test
    void decayLowersTheModAddedIncrease() {
        MarketDemandState recentDemand = new MarketDemandState(8.0, 8L, 100L);
        MerchantOffer recent = emeraldPurchase(10);
        MerchantOffer decayed = emeraldPurchase(10);

        VillagerDemandPricing.apply(
                recent,
                recentDemand.scoreAt(100L, DEMAND),
                DEMAND,
                PRICE
        );
        VillagerDemandPricing.apply(
                decayed,
                recentDemand.scoreAt(104L, DEMAND),
                DEMAND,
                PRICE
        );

        assertEquals(9, recent.getResult().getCount());
        assertEquals(10, decayed.getResult().getCount());
    }

    @Test
    void itemCountNeverExceedsItsValidStackSize() {
        MerchantOffer offer = itemPayment(60);

        VillagerDemandPricing.apply(offer, DEMAND.maximum(), DEMAND, PRICE);

        assertEquals(64, offer.getCostA().getCount());
        assertTrue(offer.getCostA().getCount() <= offer.getBaseCostA().getMaxStackSize());
    }

    @Test
    void fourProductsDoNotChangeWhenOneItemWouldExceedTenPercent() {
        MerchantOffer offer = emeraldPurchase(4);

        VillagerDemandPricing.apply(offer, DEMAND.maximum(), DEMAND, PRICE);

        assertEquals(1, offer.getCostA().getCount());
        assertEquals(4, offer.getResult().getCount());
        assertEquals(4, offer.assemble().getCount());
    }

    @Test
    void tenProductsCanReduceToNineWithinTenPercent() {
        MerchantOffer offer = emeraldPurchase(10);

        VillagerDemandPricing.apply(offer, DEMAND.maximum(), DEMAND, PRICE);

        assertEquals(1, offer.getCostA().getCount());
        assertEquals(9, offer.getResult().getCount());
    }

    @Test
    void nonEmeraldPaymentCanIncreaseByTwelveAndAHalfPercent() {
        MerchantOffer offer = itemPayment(20);

        VillagerDemandPricing.apply(
                offer,
                PRICE.demandScoreForMaximumPrice(),
                DEMAND,
                PRICE
        );

        assertEquals(22, offer.getCostA().getCount());
        assertEquals(1, offer.getResult().getCount());
    }

    @Test
    void repeatedLiveRecalculationDoesNotCompoundItsOwnDelta() {
        MerchantOffer offer = itemPayment(20);

        VillagerDemandPricing.apply(offer, 4.0, DEMAND, PRICE);
        assertEquals(21, offer.getCostA().getCount());

        VillagerDemandPricing.apply(offer, 4.0, DEMAND, PRICE);
        assertEquals(21, offer.getCostA().getCount());
    }

    @Test
    void nonEmeraldPaymentCannotFallBeforeSleep() {
        MerchantOffer offer = itemPayment(20);

        VillagerDemandPricing.apply(offer, 8.0, DEMAND, PRICE);
        assertEquals(22, offer.getCostA().getCount());

        VillagerDemandPricing.apply(offer, 4.0, DEMAND, PRICE);
        assertEquals(22, offer.getCostA().getCount());

        ((DemandPriceOffer) offer).villagerPotential$resetDemandPrice();
        VillagerDemandPricing.apply(offer, 4.0, DEMAND, PRICE);
        assertEquals(21, offer.getCostA().getCount());
    }

    @Test
    void disablingPriceSystemClearsRetainedNonEmeraldPrice() {
        MarketDemandPriceConfig disabled = new MarketDemandPriceConfig(false, 1.0, 2.0);
        MerchantOffer offer = itemPayment(20);

        VillagerDemandPricing.apply(offer, 8.0, DEMAND, PRICE);
        assertEquals(22, offer.getCostA().getCount());

        VillagerDemandPricing.apply(offer, 8.0, DEMAND, disabled);
        assertEquals(20, offer.getCostA().getCount());
    }

    @Test
    void loweringConfiguredCapClampsPreviouslyRetainedPrice() {
        MarketDemandPriceConfig twentyPercent = new MarketDemandPriceConfig(
                true,
                1.0,
                2.0,
                0.10,
                0.20
        );
        MerchantOffer offer = itemPayment(20);

        VillagerDemandPricing.apply(offer, 8.0, DEMAND, twentyPercent);
        assertEquals(24, offer.getCostA().getCount());

        VillagerDemandPricing.apply(offer, 8.0, DEMAND, PRICE);
        assertEquals(22, offer.getCostA().getCount());
    }

    @Test
    void disabledPriceSystemLeavesInputAndResultUnchanged() {
        MarketDemandPriceConfig disabled = new MarketDemandPriceConfig(false, 1.0, 2.0);
        MerchantOffer offer = emeraldPurchase(8);

        VillagerDemandPricing.apply(offer, DEMAND.maximum(), DEMAND, disabled);

        assertEquals(1, offer.getCostA().getCount());
        assertEquals(8, offer.getResult().getCount());
    }

    private static MerchantOffer offer(int emeraldCost) {
        return offer(emeraldCost, 0.05F, 0);
    }

    private static MerchantOffer offer(
            int emeraldCost,
            float vanillaPriceMultiplier,
            int vanillaDemand
    ) {
        return new MerchantOffer(
                new ItemCost(Items.EMERALD, emeraldCost),
                Optional.empty(),
                new ItemStack(Items.BOOK),
                0,
                12,
                1,
                vanillaPriceMultiplier,
                vanillaDemand
        );
    }

    private static MerchantOffer emeraldPurchase(int resultCount) {
        return new MerchantOffer(
                new ItemCost(Items.EMERALD, 1),
                Optional.empty(),
                new ItemStack(Items.APPLE, resultCount),
                0,
                12,
                1,
                0.05F,
                0
        );
    }

    private static MerchantOffer itemPayment(int itemCount) {
        return new MerchantOffer(
                new ItemCost(Items.WHEAT, itemCount),
                Optional.empty(),
                new ItemStack(Items.EMERALD),
                0,
                12,
                1,
                0.05F,
                0
        );
    }
}
