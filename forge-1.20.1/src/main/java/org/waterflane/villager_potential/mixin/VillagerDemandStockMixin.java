package org.waterflane.villager_potential.mixin;

import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.waterflane.villager_potential.VillagerDemandStock;

/** Runs only after vanilla's workstation-driven restock has completed. */
@Mixin(Villager.class)
public abstract class VillagerDemandStockMixin {
    @Inject(method = "restock", at = @At("RETURN"))
    private void villagerPotential$applyDemandStock(CallbackInfo callback) {
        VillagerDemandStock.afterVanillaRestock((Villager) (Object) this);
    }
}
