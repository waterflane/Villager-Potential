package org.waterflane.villager_potential;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.waterflane.villager_potential.core.ProfessionId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Converts between core profession identifiers and Minecraft registry values.
 */
public final class VillagerProfessionIds {
    private static final List<ProfessionId> SUPPORTED_VANILLA_PROFESSIONS = List.of(
            vanilla("armorer"),
            vanilla("butcher"),
            vanilla("cartographer"),
            vanilla("cleric"),
            vanilla("farmer"),
            vanilla("fisherman"),
            vanilla("fletcher"),
            vanilla("leatherworker"),
            vanilla("librarian"),
            vanilla("mason"),
            vanilla("shepherd"),
            vanilla("toolsmith"),
            vanilla("weaponsmith")
    );

    private VillagerProfessionIds() {
    }

    public static List<ProfessionId> supportedVanillaProfessions() {
        return SUPPORTED_VANILLA_PROFESSIONS;
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

    private static ProfessionId vanilla(String path) {
        return new ProfessionId(ResourceLocation.DEFAULT_NAMESPACE, path);
    }
}
