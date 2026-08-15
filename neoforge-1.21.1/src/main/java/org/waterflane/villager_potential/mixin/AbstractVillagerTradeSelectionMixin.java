package org.waterflane.villager_potential.mixin;

import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.waterflane.villager_potential.SpecializedTradeSelection;

@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerTradeSelectionMixin {
    @Inject(method = "addOffersFromItemListings", at = @At("HEAD"), cancellable = true)
    private void villagerPotential$applySpecializationWeights(
            MerchantOffers offers,
            VillagerTrades.ItemListing[] candidates,
            int requestedOfferCount,
            CallbackInfo callback
    ) {
        if ((Object) this instanceof Villager villager
                && SpecializedTradeSelection.tryAddOffers(
                        villager,
                        offers,
                        candidates,
                        requestedOfferCount
                )) {
            callback.cancel();
        }
    }
}
