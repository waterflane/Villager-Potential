package org.waterflane.villager_potential.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.waterflane.villager_potential.VillagerPotentialAttachments;

@Mixin(Villager.class)
public abstract class VillagerBreedingMixin {
    @Inject(
            method = "getBreedOffspring(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/AgeableMob;)Lnet/minecraft/world/entity/npc/Villager;",
            at = @At("RETURN")
    )
    private void villagerPotential$inheritPotential(
            ServerLevel level,
            AgeableMob otherParent,
            CallbackInfoReturnable<Villager> callback
    ) {
        Villager child = callback.getReturnValue();
        if (child != null && otherParent instanceof Villager secondParent) {
            VillagerPotentialAttachments.inherit((Villager) (Object) this, secondParent, child);
        }
    }
}
