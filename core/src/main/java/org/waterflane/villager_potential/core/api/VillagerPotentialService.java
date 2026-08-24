package org.waterflane.villager_potential.core.api;

import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.SpecializationId;

import java.util.Optional;
import java.util.UUID;

/**
 * Small, platform-neutral SPI for companion bridges.
 *
 * <p>Version one addresses loaded villagers by UUID, returns immutable views,
 * and exposes only the supported specialization assignment mutation.</p>
 */
public interface VillagerPotentialService {
    int API_VERSION = 1;

    int apiVersion();

    Optional<PotentialView> find(UUID villagerId);

    Optional<PotentialView> assignSpecialization(
            UUID villagerId,
            ProfessionId profession,
            SpecializationId specialization
    );
}
