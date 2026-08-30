package org.waterflane.villager_potential.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.waterflane.villager_potential.BulkTradeContext;

/** Marks the synchronous trade loop performed by Shift-clicking the result slot. */
@Mixin(MerchantMenu.class)
public abstract class MerchantMenuBulkTradeMixin {
    @Unique
    private static final int RESULT_SLOT = 2;

    @Inject(method = "quickMoveStack", at = @At("HEAD"))
    private void villagerPotential$beginBulkTrade(
            Player player,
            int slot,
            CallbackInfoReturnable<ItemStack> callback
    ) {
        if (slot == RESULT_SLOT) {
            BulkTradeContext.begin();
        }
    }

    @Inject(method = "quickMoveStack", at = @At("RETURN"))
    private void villagerPotential$endBulkTrade(
            Player player,
            int slot,
            CallbackInfoReturnable<ItemStack> callback
    ) {
        if (slot == RESULT_SLOT) {
            BulkTradeContext.end();
        }
    }
}
