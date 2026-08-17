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
    void highDemandIncreasesWithinConfiguredCap() {
        MerchantOffer offer = offer(20);

        VillagerDemandPricing.apply(offer, DEMAND.maximum(), DEMAND, PRICE);

        assertEquals(40, offer.getCostA().getCount());
        assertTrue(offer.getCostA().getCount() <= 20 * PRICE.maximumMultiplier());
    }

    @Test
    void vanillaDiscountStillApplies() {
        MerchantOffer offer = offer(20, 0.5F, 1);
        offer.setSpecialPriceDiff(-5);
        assertEquals(25, offer.getCostA().getCount());

        VillagerDemandPricing.apply(offer, DEMAND.maximum(), DEMAND, PRICE);

        assertEquals(45, offer.getCostA().getCount());
    }

    @Test
    void decayLowersTheModAddedIncrease() {
        MarketDemandState recentDemand = new MarketDemandState(100.0, 10L, 100L);
        MerchantOffer recent = offer(20);
        MerchantOffer decayed = offer(20);

        VillagerDemandPricing.apply(
                recent,
                recentDemand.scoreAt(100L, DEMAND),
                DEMAND,
                PRICE
        );
        VillagerDemandPricing.apply(
                decayed,
                recentDemand.scoreAt(150L, DEMAND),
                DEMAND,
                PRICE
        );

        assertEquals(40, recent.getCostA().getCount());
        assertEquals(30, decayed.getCostA().getCount());
    }

    @Test
    void itemCountNeverExceedsItsValidStackSize() {
        MerchantOffer offer = offer(40);

        VillagerDemandPricing.apply(offer, DEMAND.maximum(), DEMAND, PRICE);

        assertEquals(64, offer.getCostA().getCount());
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
}
