package org.waterflane.villager_potential.core.api;

import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.SpecializationId;

import java.util.Optional;
import java.util.UUID;

/**
 * Small, platform-neutral SPI for companion bridges.
 *
 * <p>Version two addresses loaded villagers by UUID, returns immutable views,
 * and exposes the deliberately small set of supported mutations used by
 * administrative tools and future companion bridges.</p>
 */
public interface VillagerPotentialService {
    int API_VERSION = 2;

    int apiVersion();

    Optional<PotentialView> find(UUID villagerId);

    Optional<PotentialView> assignSpecialization(
            UUID villagerId,
            ProfessionId profession,
            SpecializationId specialization
    );

    /** Sets one finite, non-negative aptitude without changing derived state. */
    Optional<PotentialView> setAptitude(
            UUID villagerId,
            ProfessionId profession,
            double aptitude
    );

    /** Sets finite, non-negative skill on an existing career, preserving tenure. */
    Optional<PotentialView> setSkill(
            UUID villagerId,
            ProfessionId profession,
            double skill
    );

    /** Clears only this profession's career-derived state and preserves its aptitude. */
    Optional<PotentialView> resetProfession(UUID villagerId, ProfessionId profession);

    /** Explicitly clears one profession's derived state and regenerates its aptitude. */
    Optional<PotentialView> regenerateProfession(UUID villagerId, ProfessionId profession);

    /** Explicitly discards and regenerates all Potential for the loaded villager. */
    Optional<PotentialView> regenerateAll(UUID villagerId);
}
