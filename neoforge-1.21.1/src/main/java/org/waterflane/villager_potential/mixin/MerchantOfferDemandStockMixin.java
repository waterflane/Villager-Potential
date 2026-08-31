package org.waterflane.villager_potential.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.waterflane.villager_potential.DemandPriceOffer;
import org.waterflane.villager_potential.DemandStockOffer;

/** Makes a bounded restock ceiling participate in vanilla offer operations. */
@Mixin(MerchantOffer.class)
public abstract class MerchantOfferDemandStockMixin implements DemandStockOffer, DemandPriceOffer {
    @Shadow
    @Final
    private int maxUses;

    @Shadow
    private int uses;

    @Shadow
    private int demand;

    @Shadow
    @Final
    private ItemStack result;

    @Shadow
    private int specialPriceDiff;

    @Unique
    private int villagerPotential$effectiveMaximumUses;

    @Unique
    private int villagerPotential$effectiveResultCount;

    @Unique
    private int villagerPotential$demandInputDelta;

    @Unique
    private int villagerPotential$demandInputPriceFloor;

    @Override
    public int villagerPotential$baseMaximumUses() {
        return maxUses;
    }

    @Override
    public void villagerPotential$setEffectiveMaximumUses(int maximumUses) {
        villagerPotential$effectiveMaximumUses = Math.max(maxUses, maximumUses);
    }

    @Override
    public ItemStack villagerPotential$baseResult() {
        return result;
    }

    @Override
    public int villagerPotential$baseResultCount() {
        return result.getCount();
    }

    @Override
    public void villagerPotential$clearDemandPriceAdjustment() {
        specialPriceDiff -= villagerPotential$demandInputDelta;
        villagerPotential$demandInputDelta = 0;
        villagerPotential$effectiveResultCount = 0;
    }

    @Override
    public void villagerPotential$applyDemandInputDelta(int delta) {
        villagerPotential$demandInputDelta = delta;
        specialPriceDiff += delta;
    }

    @Override
    public int villagerPotential$retainDemandInputPrice(
            int proposedPrice,
            int maximumPrice,
            boolean demandActive
    ) {
        if (!demandActive && villagerPotential$demandInputPriceFloor == 0) {
            return proposedPrice;
        }
        villagerPotential$demandInputPriceFloor = Math.min(
                maximumPrice,
                Math.max(villagerPotential$demandInputPriceFloor, proposedPrice)
        );
        return villagerPotential$demandInputPriceFloor;
    }

    @Override
    public void villagerPotential$clearDemandInputPriceFloor() {
        villagerPotential$demandInputPriceFloor = 0;
    }

    @Override
    public void villagerPotential$setEffectiveResultCount(int resultCount) {
        int baseCount = villagerPotential$baseResultCount();
        villagerPotential$effectiveResultCount = Math.max(1, Math.min(baseCount, resultCount));
    }

    @Override
    public void villagerPotential$resetDemandPrice() {
        villagerPotential$clearDemandPriceAdjustment();
        villagerPotential$clearDemandInputPriceFloor();
        demand = 0;
    }

    @Inject(method = {"getResult", "assemble"}, at = @At("RETURN"), cancellable = true)
    private void villagerPotential$applyEffectiveResultCount(
            CallbackInfoReturnable<ItemStack> callback
    ) {
        ItemStack returned = callback.getReturnValue();
        if (villagerPotential$effectiveResultCount > 0
                && villagerPotential$effectiveResultCount < returned.getCount()) {
            ItemStack adjusted = returned.copy();
            adjusted.setCount(villagerPotential$effectiveResultCount);
            callback.setReturnValue(adjusted);
        }
    }

    @Inject(method = "resetSpecialPriceDiff", at = @At("RETURN"))
    private void villagerPotential$forgetResetInputDelta(CallbackInfo callback) {
        villagerPotential$demandInputDelta = 0;
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
