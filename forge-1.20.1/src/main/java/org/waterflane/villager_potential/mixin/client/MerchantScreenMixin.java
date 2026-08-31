package org.waterflane.villager_potential.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.waterflane.villager_potential.client.VillagerTradeProgressOverlay;

/** Adds the two synchronized progression bars after the vanilla merchant UI. */
@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin {
    /** Hides vanilla's inaccurate "restocks twice per day" tooltip. */
    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/MerchantMenu;canRestock()Z"
            )
    )
    private boolean villagerPotential$hideVanillaRestockTooltip(MerchantMenu menu) {
        return false;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void villagerPotential$renderProgressBars(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo callback
    ) {
        VillagerTradeProgressOverlay.render(
                (MerchantScreen) (Object) this,
                graphics,
                mouseX,
                mouseY
        );
    }
}
