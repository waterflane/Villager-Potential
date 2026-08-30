package org.waterflane.villager_potential;

import com.mojang.serialization.DataResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.Objects;

/** Mutable Forge capability holder around the immutable core state. */
public final class VillagerPotentialCapability {
    private VillagerPotentialState state = VillagerPotentialState.createDefault();

    public VillagerPotentialState state() {
        return state;
    }

    public void setState(VillagerPotentialState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    public CompoundTag serialize() {
        DataResult<net.minecraft.nbt.Tag> encoded = VillagerPotentialAttachments.CODEC
                .encodeStart(NbtOps.INSTANCE, state);
        net.minecraft.nbt.Tag tag = encoded.resultOrPartial(message ->
                VillagerPotentialDiagnostics.persistence("encode", message)
        ).orElseGet(CompoundTag::new);
        return tag instanceof CompoundTag compound ? compound : new CompoundTag();
    }

    public void deserialize(CompoundTag tag) {
        VillagerPotentialAttachments.CODEC.parse(NbtOps.INSTANCE, tag)
                .resultOrPartial(message ->
                        VillagerPotentialDiagnostics.persistence("decode", message)
                )
                .ifPresent(this::setState);
    }
}
