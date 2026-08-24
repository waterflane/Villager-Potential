package org.waterflane.villager_potential.core;

import java.util.Objects;
import java.util.random.RandomGenerator;

/** Lazily provisions aptitude for professions discovered after initialization. */
public final class AptitudeProvisioning {
    private AptitudeProvisioning() {
    }

    public static VillagerPotentialState ensure(
            VillagerPotentialState state,
            ProfessionId profession,
            AptitudeGenerationConfig config,
            RandomGenerator random
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(profession, "profession");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(random, "random");
        return state.aptitudeFor(profession).isPresent()
                ? state
                : state.withAptitude(profession, AptitudeGenerator.generate(config, random));
    }
}
