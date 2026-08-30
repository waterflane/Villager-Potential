package org.waterflane.villager_potential;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;

/** Platform-neutral root NBT mirror used when worlds move between loaders. */
public final class VillagerPotentialPortableData {
    public static final String ROOT_KEY = "villager_potential:data";
    private static final String NATIVE_ROOT = "ForgeCaps";
    private static final String NATIVE_KEY = "villager_potential:potential";

    private VillagerPotentialPortableData() {
    }

    public static void write(Entity entity, CompoundTag root) {
        if (!supported(entity)) {
            return;
        }
        VillagerPotentialAttachments.CODEC.encodeStart(
                        NbtOps.INSTANCE,
                        VillagerPotentialAttachments.stored(entity)
                )
                .resultOrPartial(message ->
                        VillagerPotentialDiagnostics.persistence("portable encode", message))
                .filter(CompoundTag.class::isInstance)
                .map(CompoundTag.class::cast)
                .ifPresent(tag -> root.put(ROOT_KEY, tag));
    }

    public static void read(Entity entity, CompoundTag root) {
        if (!supported(entity) || !root.contains(ROOT_KEY, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag portable = root.getCompound(ROOT_KEY);
        int portableSchema = schema(portable);
        int nativeSchema = nativeSchema(root);
        if (portableSchema < nativeSchema) {
            return;
        }
        VillagerPotentialAttachments.CODEC.parse(NbtOps.INSTANCE, portable)
                .resultOrPartial(message ->
                        VillagerPotentialDiagnostics.persistence("portable decode", message))
                .ifPresent(state -> VillagerPotentialAttachments.store(entity, state));
    }

    private static int nativeSchema(CompoundTag root) {
        if (!root.contains(NATIVE_ROOT, Tag.TAG_COMPOUND)) {
            return -1;
        }
        CompoundTag caps = root.getCompound(NATIVE_ROOT);
        return caps.contains(NATIVE_KEY, Tag.TAG_COMPOUND)
                ? schema(caps.getCompound(NATIVE_KEY))
                : -1;
    }

    private static int schema(CompoundTag tag) {
        return tag.contains("schema_version", Tag.TAG_ANY_NUMERIC)
                ? tag.getInt("schema_version")
                : 0;
    }

    private static boolean supported(Entity entity) {
        return entity instanceof Villager || entity instanceof ZombieVillager;
    }
}
