package org.waterflane.villager_potential.mixin;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.waterflane.villager_potential.VillagerDemandPricing;

/** Layers Potential demand on top of vanilla's completed special-price pass. */
@Mixin(Villager.class)
public abstract class VillagerDemandPricingMixin {
    @Inject(method = "updateSpecialPrices", at = @At("RETURN"))
    private void villagerPotential$applyDemandPrices(Player player, CallbackInfo callback) {
        VillagerDemandPricing.apply((Villager) (Object) this);
    }
}
