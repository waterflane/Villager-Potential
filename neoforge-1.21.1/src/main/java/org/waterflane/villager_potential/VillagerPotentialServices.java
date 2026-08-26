package org.waterflane.villager_potential;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.SpecializationId;
import org.waterflane.villager_potential.core.api.PotentialView;
import org.waterflane.villager_potential.core.api.VillagerPotentialService;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Creates the supported versioned service for a logical NeoForge server. */
public final class VillagerPotentialServices {
    private VillagerPotentialServices() {
    }

    /** The returned service must be called on the logical server thread. */
    public static VillagerPotentialService forServer(MinecraftServer server) {
        return new NeoForgeService(Objects.requireNonNull(server, "server"));
    }

    private static final class NeoForgeService implements VillagerPotentialService {
        private final MinecraftServer server;

        private NeoForgeService(MinecraftServer server) {
            this.server = server;
        }

        @Override
        public int apiVersion() {
            return API_VERSION;
        }

        @Override
        public Optional<PotentialView> find(UUID villagerId) {
            return findVillager(villagerId).map(VillagerPotentialApi::view);
        }

        @Override
        public Optional<PotentialView> assignSpecialization(
                UUID villagerId,
                ProfessionId profession,
                SpecializationId specialization
        ) {
            Objects.requireNonNull(profession, "profession");
            Objects.requireNonNull(specialization, "specialization");
            return findVillager(villagerId).map(villager ->
                    VillagerPotentialApi.assignSpecialization(
                            villager,
                            profession,
                            specialization
                    )
            );
        }

        @Override
        public Optional<PotentialView> setAptitude(
                UUID villagerId,
                ProfessionId profession,
                double aptitude
        ) {
            Objects.requireNonNull(profession, "profession");
            return findVillager(villagerId).map(villager ->
                    VillagerPotentialApi.setAptitude(villager, profession, aptitude)
            );
        }

        @Override
        public Optional<PotentialView> setSkill(
                UUID villagerId,
                ProfessionId profession,
                double skill
        ) {
            Objects.requireNonNull(profession, "profession");
            return findVillager(villagerId).map(villager ->
                    VillagerPotentialApi.setSkill(villager, profession, skill)
            );
        }

        @Override
        public Optional<PotentialView> resetProfession(
                UUID villagerId,
                ProfessionId profession
        ) {
            Objects.requireNonNull(profession, "profession");
            return findVillager(villagerId).map(villager ->
                    VillagerPotentialApi.resetProfession(villager, profession)
            );
        }

        @Override
        public Optional<PotentialView> regenerateProfession(
                UUID villagerId,
                ProfessionId profession
        ) {
            Objects.requireNonNull(profession, "profession");
            return findVillager(villagerId).map(villager ->
                    VillagerPotentialApi.regenerateProfession(villager, profession)
            );
        }

        @Override
        public Optional<PotentialView> regenerateAll(UUID villagerId) {
            return findVillager(villagerId).map(VillagerPotentialApi::regenerateAll);
        }

        private Optional<Villager> findVillager(UUID villagerId) {
            Objects.requireNonNull(villagerId, "villagerId");
            for (var level : server.getAllLevels()) {
                Entity entity = level.getEntity(villagerId);
                if (entity instanceof Villager villager) {
                    return Optional.of(villager);
                }
            }
            return Optional.empty();
        }
    }
}
