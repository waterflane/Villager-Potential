package org.waterflane.villager_potential.mixin;

import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.waterflane.villager_potential.VillagerLevelUpAccess;

/**
 * Makes Potential the only authority that may schedule a profession level-up,
 * while retaining vanilla's delayed application and trade-unlock code.
 */
@Mixin(Villager.class)
public abstract class VillagerLevelUpMixin implements VillagerLevelUpAccess {
    @Shadow
    private int updateMerchantTimer;

    @Shadow
    private boolean increaseProfessionLevelOnUpdate;

    @Inject(method = "shouldIncreaseLevel", at = @At("HEAD"), cancellable = true)
    private void villagerPotential$disableTradeXpLevelUp(
            CallbackInfoReturnable<Boolean> callback
    ) {
        callback.setReturnValue(false);
    }

    @Override
    public boolean villagerPotential$queueLevelUp() {
        if (increaseProfessionLevelOnUpdate) {
            return false;
        }
        updateMerchantTimer = 40;
        increaseProfessionLevelOnUpdate = true;
        return true;
    }
}
