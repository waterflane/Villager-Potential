package org.waterflane.villager_potential;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.waterflane.villager_potential.core.ProfessionId;

import java.util.Objects;
import java.util.Optional;

/**
 * Converts between core profession identifiers and Minecraft registry values.
 */
public final class VillagerProfessionIds {
    private VillagerProfessionIds() {
    }

    public static ProfessionId fromMinecraft(VillagerProfession profession) {
        ResourceLocation registryName = BuiltInRegistries.VILLAGER_PROFESSION.getKey(
                Objects.requireNonNull(profession, "profession")
        );
        if (registryName == null) {
            throw new IllegalArgumentException("Villager profession is not registered");
        }

        return new ProfessionId(registryName.getNamespace(), registryName.getPath());
    }

    public static Optional<VillagerProfession> toMinecraft(ProfessionId professionId) {
        Objects.requireNonNull(professionId, "professionId");
        ResourceLocation registryName = ResourceLocation.fromNamespaceAndPath(
                professionId.namespace(),
                professionId.path()
        );
        if (!BuiltInRegistries.VILLAGER_PROFESSION.containsKey(registryName)) {
            return Optional.empty();
        }

        return Optional.of(BuiltInRegistries.VILLAGER_PROFESSION.get(registryName));
    }
}
