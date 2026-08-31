package org.waterflane.villager_potential.mixin;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.waterflane.villager_potential.VillagerDemandPricing;

/** Reprices only after the current purchase has fully consumed its old payment. */
@Mixin(MerchantResultSlot.class)
public abstract class MerchantResultSlotTradeMixin {
    @Shadow
    @Final
    private MerchantContainer slots;

    @Shadow
    @Final
    private Merchant merchant;

    @Unique
    private MerchantOffer villagerPotential$activeOfferBeforePurchase;

    @Unique
    private int villagerPotential$usesBeforePurchase;

    @Inject(method = "onTake", at = @At("HEAD"))
    private void villagerPotential$captureActiveOffer(
            Player player,
            ItemStack purchased,
            CallbackInfo callback
    ) {
        villagerPotential$activeOfferBeforePurchase = slots.getActiveOffer();
        villagerPotential$usesBeforePurchase = villagerPotential$activeOfferBeforePurchase == null
                ? 0
                : villagerPotential$activeOfferBeforePurchase.getUses();
    }

    @Inject(method = "onTake", at = @At("RETURN"))
    private void villagerPotential$refreshCompletedTrade(
            Player player,
            ItemStack purchased,
            CallbackInfo callback
    ) {
        if (!(merchant instanceof Villager villager)) {
            return;
        }
        MerchantOffer completedOffer = villagerPotential$activeOfferBeforePurchase;
        villagerPotential$activeOfferBeforePurchase = null;
        if (completedOffer == null
                || completedOffer.getUses() <= villagerPotential$usesBeforePurchase) {
            return;
        }

        VillagerDemandPricing.apply(villager, completedOffer);
        slots.updateSellItem();
        VillagerDemandPricing.syncOpenMenu(villager);
        player.containerMenu.broadcastChanges();
    }
}
