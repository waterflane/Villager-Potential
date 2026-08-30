package org.waterflane.villager_potential.mixin;

import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.waterflane.villager_potential.Config;
import org.waterflane.villager_potential.SpecializedTradeSelection;
import org.waterflane.villager_potential.VillagerPotentialAttachments;

@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerTradeSelectionMixin {
    @Unique
    private int villagerPotential$offersBeforeGeneration;

    @Unique
    private boolean villagerPotential$recordedWeightedGeneration;

    @Inject(method = "addOffersFromItemListings", at = @At("HEAD"), cancellable = true)
    private void villagerPotential$applySpecializationWeights(
            MerchantOffers offers,
            VillagerTrades.ItemListing[] candidates,
            int requestedOfferCount,
            CallbackInfo callback
    ) {
        villagerPotential$offersBeforeGeneration = offers.size();
        villagerPotential$recordedWeightedGeneration = false;
        if ((Object) this instanceof Villager villager
                && SpecializedTradeSelection.tryAddOffers(
                        villager,
                        offers,
                        candidates,
                        requestedOfferCount
                )) {
            VillagerPotentialAttachments.recordGeneratedOffers(
                    villager,
                    offers,
                    villagerPotential$offersBeforeGeneration,
                    villager.level().getGameTime(),
                    Config.tradeHistoryMaximumEntries()
            );
            villagerPotential$recordedWeightedGeneration = true;
            callback.cancel();
        }
    }

    @Inject(method = "addOffersFromItemListings", at = @At("RETURN"))
    private void villagerPotential$recordVanillaGeneratedOffers(
            MerchantOffers offers,
            VillagerTrades.ItemListing[] candidates,
            int requestedOfferCount,
            CallbackInfo callback
    ) {
        if (!villagerPotential$recordedWeightedGeneration
                && (Object) this instanceof Villager villager) {
            VillagerPotentialAttachments.recordGeneratedOffers(
                    villager,
                    offers,
                    villagerPotential$offersBeforeGeneration,
                    villager.level().getGameTime(),
                    Config.tradeHistoryMaximumEntries()
            );
        }
    }
}
