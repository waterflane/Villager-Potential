package org.waterflane.villager_potential.mixin;

import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.waterflane.villager_potential.DemandStockOffer;

/** Makes a bounded restock ceiling participate in vanilla offer operations. */
@Mixin(MerchantOffer.class)
public abstract class MerchantOfferDemandStockMixin implements DemandStockOffer {
    @Shadow
    @Final
    private int maxUses;

    @Shadow
    private int uses;

    @Shadow
    private int demand;

    @Unique
    private int villagerPotential$effectiveMaximumUses;

    @Override
    public int villagerPotential$baseMaximumUses() {
        return maxUses;
    }

    @Override
    public void villagerPotential$setEffectiveMaximumUses(int maximumUses) {
        villagerPotential$effectiveMaximumUses = Math.max(maxUses, maximumUses);
    }

    @Inject(method = "getMaxUses", at = @At("RETURN"), cancellable = true)
    private void villagerPotential$reportEffectiveMaximum(
            CallbackInfoReturnable<Integer> callback
    ) {
        if (villagerPotential$effectiveMaximumUses > maxUses) {
            callback.setReturnValue(villagerPotential$effectiveMaximumUses);
        }
    }

    @Inject(method = "isOutOfStock", at = @At("HEAD"), cancellable = true)
    private void villagerPotential$checkEffectiveMaximum(
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (villagerPotential$effectiveMaximumUses > maxUses) {
            callback.setReturnValue(uses >= villagerPotential$effectiveMaximumUses);
        }
    }

    @Inject(method = "setToOutOfStock", at = @At("RETURN"))
    private void villagerPotential$useEffectiveMaximum(CallbackInfo callback) {
        if (villagerPotential$effectiveMaximumUses > maxUses) {
            uses = villagerPotential$effectiveMaximumUses;
        }
    }

    /** Preserve vanilla's demand formula, using the same effective ceiling. */
    @Inject(method = "updateDemand", at = @At("HEAD"), cancellable = true)
    private void villagerPotential$updateDemandForEffectiveMaximum(CallbackInfo callback) {
        if (villagerPotential$effectiveMaximumUses > maxUses) {
            demand += uses - (villagerPotential$effectiveMaximumUses - uses);
            callback.cancel();
        }
    }
}
