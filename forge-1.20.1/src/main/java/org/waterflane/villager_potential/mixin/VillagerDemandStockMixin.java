package org.waterflane.villager_potential.mixin;

import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.waterflane.villager_potential.VillagerDemandStock;
import org.waterflane.villager_potential.VillagerDemandPricing;
import org.waterflane.villager_potential.VillagerPotentialAttachments;

/** Moves restocking from the workstation schedule to completed villager sleep. */
@Mixin(Villager.class)
public abstract class VillagerDemandStockMixin {
    @Unique
    private boolean villagerPotential$wasSleeping;

    @Unique
    private boolean villagerPotential$sleepRestockAllowed;

    /** WorkAtPoi must never perform the old twice-per-day restock. */
    @Inject(method = "shouldRestock", at = @At("HEAD"), cancellable = true)
    private void villagerPotential$disableWorkstationRestock(
            CallbackInfoReturnable<Boolean> callback
    ) {
        callback.setReturnValue(false);
    }

    /** Reject direct and modded restocks unless this wake-up is performing it. */
    @Inject(method = "restock", at = @At("HEAD"), cancellable = true)
    private void villagerPotential$allowOnlySleepRestock(CallbackInfo callback) {
        if (!villagerPotential$sleepRestockAllowed) {
            callback.cancel();
        }
    }

    @Inject(method = "stopSleeping", at = @At("HEAD"))
    private void villagerPotential$captureCompletedSleep(CallbackInfo callback) {
        villagerPotential$wasSleeping = ((Villager) (Object) this).isSleeping();
    }

    @Inject(method = "stopSleeping", at = @At("RETURN"))
    private void villagerPotential$restockAfterSleep(CallbackInfo callback) {
        Villager villager = (Villager) (Object) this;
        if (!villagerPotential$wasSleeping || villager.level().isClientSide()) {
            return;
        }
        villagerPotential$wasSleeping = false;
        if (villager.getOffers().stream().anyMatch(offer -> offer.getUses() > 0)) {
            villagerPotential$sleepRestockAllowed = true;
            try {
                villager.restock();
            } finally {
                villagerPotential$sleepRestockAllowed = false;
            }
        }
        VillagerPotentialAttachments.resetMarketDemandAfterSleep(villager);
        VillagerDemandPricing.resetAfterSleep(villager);
    }

    @Inject(method = "restock", at = @At("RETURN"))
    private void villagerPotential$applyDemandStock(CallbackInfo callback) {
        if (villagerPotential$sleepRestockAllowed) {
            VillagerDemandStock.afterVanillaRestock((Villager) (Object) this);
        }
    }
}
