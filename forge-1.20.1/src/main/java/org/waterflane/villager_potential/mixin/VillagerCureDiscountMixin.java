package org.waterflane.villager_potential.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.waterflane.villager_potential.core.CureDiscountPolicy;

/** Prevents repeated infection and curing from stacking the same player's discount. */
@Mixin(Villager.class)
public abstract class VillagerCureDiscountMixin {
    @Shadow
    @Final
    private GossipContainer gossips;

    @Inject(method = "onReputationEventFrom", at = @At("HEAD"), cancellable = true)
    private void villagerPotential$limitCureDiscount(
            ReputationEventType type,
            Entity target,
            CallbackInfo callback
    ) {
        if (type != ReputationEventType.ZOMBIE_VILLAGER_CURED) {
            return;
        }
        int permanentReputation = gossips.getReputation(
                target.getUUID(),
                gossipType -> gossipType == GossipType.MAJOR_POSITIVE
        );
        if (!CureDiscountPolicy.shouldApplyCureBonus(permanentReputation)) {
            callback.cancel();
        }
    }
}
