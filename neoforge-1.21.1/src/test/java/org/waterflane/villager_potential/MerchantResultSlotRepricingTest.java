package org.waterflane.villager_potential;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MerchantResultSlotRepricingTest {
    private static final ProfessionId FARMER = ProfessionId.parse("minecraft:farmer");

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void completedPurchaseRepricesBeforeTheTradeMenuCloses() {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.WHEAT, 20),
                Optional.empty(),
                new ItemStack(Items.EMERALD),
                12,
                1,
                0.05F
        );
        VillagerPotentialState state = new VillagerPotentialState(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                Map.of(FARMER, 1.0)
        );
        for (int purchase = 0; purchase < 8; purchase++) {
            state = state.recordTradePurchase(
                    FARMER,
                    MerchantOfferTradeKeys.from(offer),
                    100L,
                    Config.marketDemandConfig()
            );
        }
        VillagerPotentialState stateWithDemand = state;

        Villager villager = mock(Villager.class);
        VillagerData data = mock(VillagerData.class);
        ServerLevel level = mock(ServerLevel.class);
        Player player = mock(Player.class);
        player.containerMenu = mock(AbstractContainerMenu.class);
        MerchantOffers offers = new MerchantOffers();
        offers.add(offer);
        when(villager.level()).thenReturn(level);
        when(level.getGameTime()).thenReturn(100L);
        when(villager.getVillagerData()).thenReturn(data);
        when(data.getProfession()).thenReturn(VillagerProfession.FARMER);
        when(villager.getUUID()).thenReturn(UUID.randomUUID());
        when(villager.getOffers()).thenReturn(offers);
        when(villager.getData(any(Supplier.class))).thenReturn(stateWithDemand);
        doAnswer(invocation -> {
            invocation.<MerchantOffer>getArgument(0).increaseUses();
            return null;
        }).when(villager).notifyTrade(any(MerchantOffer.class));

        MerchantContainer container = new MerchantContainer(villager);
        container.setItem(0, new ItemStack(Items.WHEAT, 64));
        MerchantResultSlot resultSlot = new MerchantResultSlot(
                player,
                villager,
                container,
                2,
                0,
                0
        );

        resultSlot.onTake(player, new ItemStack(Items.EMERALD));

        assertEquals(1, offer.getUses());
        assertEquals(22, offer.getCostA().getCount());
        assertEquals(44, container.getItem(0).getCount());
    }
}
