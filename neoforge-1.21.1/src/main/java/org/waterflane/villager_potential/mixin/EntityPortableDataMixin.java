package org.waterflane.villager_potential.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.waterflane.villager_potential.VillagerPotentialPortableData;

/** Mirrors Potential at a loader-independent entity NBT key. */
@Mixin(Entity.class)
public abstract class EntityPortableDataMixin {
    @Inject(method = "saveWithoutId", at = @At("RETURN"))
    private void villagerPotential$writePortableData(
            CompoundTag tag,
            CallbackInfoReturnable<CompoundTag> callback
    ) {
        VillagerPotentialPortableData.write((Entity) (Object) this, callback.getReturnValue());
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void villagerPotential$readPortableData(CompoundTag tag, CallbackInfo callback) {
        VillagerPotentialPortableData.read((Entity) (Object) this, tag);
    }
}
