package org.waterflane.villager_potential.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.waterflane.villager_potential.client.VillagerTradeProgressOverlay;

/** Clears synchronized villager data when its merchant container screen closes. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Inject(method = "removed", at = @At("TAIL"))
    private void villagerPotential$clearMerchantProgress(CallbackInfo callback) {
        if ((Object) this instanceof MerchantScreen) {
            VillagerTradeProgressOverlay.clear();
        }
    }
}
