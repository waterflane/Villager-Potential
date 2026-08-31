package org.waterflane.villager_potential;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class CompletedTradeContextTest {
    @Test
    void returnsOfferOnlyToTheVillagerWhoseTradeCompleted() {
        Villager owner = mock(Villager.class);
        Villager other = mock(Villager.class);
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.WHEAT, 20),
                Optional.empty(),
                new ItemStack(Items.EMERALD),
                12,
                1,
                0.05F
        );

        CompletedTradeContext.record(owner, offer);
        assertNull(CompletedTradeContext.takeFor(other));

        CompletedTradeContext.record(owner, offer);
        assertSame(offer, CompletedTradeContext.takeFor(owner));
        assertNull(CompletedTradeContext.takeFor(owner));
    }
}
