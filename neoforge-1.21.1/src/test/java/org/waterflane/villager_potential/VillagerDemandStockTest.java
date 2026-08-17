package org.waterflane.villager_potential;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.MarketDemandConfig;
import org.waterflane.villager_potential.core.MarketDemandStockConfig;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerDemandStockTest {
    private static final MarketDemandConfig DEMAND = new MarketDemandConfig(
            0.0,
            0.0,
            100.0,
            1.0,
            0.0
    );

    @Test
    void serverStockConfigDefaultsToConservativeDisabledMode() {
        MarketDemandStockConfig defaults = ServerConfig.DEFAULT_MARKET_DEMAND_STOCK;

        assertFalse(defaults.enabled());
        assertEquals(2, defaults.maximumAdditionalUses());
        assertEquals(16, defaults.maximumUsesPerOffer());
    }

    @Test
    void disabledModeLeavesOfferExactlyVanilla() {
        MerchantOffer offer = offer(12);
        offer.increaseUses();

        VillagerDemandStock.applyRestockedOffer(
                offer,
                DEMAND.maximum(),
                DEMAND,
                new MarketDemandStockConfig(false, 64, 64)
        );

        assertEquals(12, offer.getMaxUses());
        assertEquals(1, offer.getUses());
    }

    @Test
    void increasedDemandRaisesTheEffectiveUseCeiling() {
        MerchantOffer normalDemand = offer(12);
        MerchantOffer highDemand = offer(12);
        MarketDemandStockConfig configured = new MarketDemandStockConfig(true, 4, 20);

        VillagerDemandStock.applyRestockedOffer(
                normalDemand,
                DEMAND.baseline(),
                DEMAND,
                configured
        );
        VillagerDemandStock.applyRestockedOffer(
                highDemand,
                DEMAND.maximum(),
                DEMAND,
                configured
        );

        assertEquals(12, normalDemand.getMaxUses());
        assertEquals(16, highDemand.getMaxUses());
    }

    @Test
    void effectiveOfferStockHonorsTheAbsoluteCap() {
        MerchantOffer offer = offer(12);

        VillagerDemandStock.applyRestockedOffer(
                offer,
                DEMAND.maximum(),
                DEMAND,
                new MarketDemandStockConfig(true, 64, 15)
        );

        assertEquals(15, offer.getMaxUses());
        for (int use = 0; use < 15; use++) {
            assertFalse(offer.isOutOfStock());
            offer.increaseUses();
        }
        assertTrue(offer.isOutOfStock());
    }

    @Test
    void stockPolicyDoesNotPerformOrFakeAVanillaRestock() {
        MerchantOffer offer = offer(12);
        offer.increaseUses();
        offer.increaseUses();

        VillagerDemandStock.applyRestockedOffer(
                offer,
                DEMAND.maximum(),
                DEMAND,
                new MarketDemandStockConfig(true, 4, 20)
        );

        assertEquals(2, offer.getUses());
        assertEquals(12, offer.getMaxUses());
        assertTrue(offer.needsRestock());
    }

    private static MerchantOffer offer(int maximumUses) {
        return new MerchantOffer(
                new ItemCost(Items.EMERALD, 1),
                Optional.empty(),
                new ItemStack(Items.BOOK),
                0,
                maximumUses,
                1,
                0.05F,
                0
        );
    }
}
