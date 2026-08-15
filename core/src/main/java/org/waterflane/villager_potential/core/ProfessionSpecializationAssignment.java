package org.waterflane.villager_potential.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Assigns one stable specialization when a profession career is first created.
 */
public final class ProfessionSpecializationAssignment {
    private ProfessionSpecializationAssignment() {
    }

    public static VillagerPotentialState enterProfession(
            VillagerPotentialState state,
            ProfessionId professionId,
            long assignmentTime,
            Optional<ProfessionSpecializationDefinition> definition,
            RandomGenerator random
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(professionId, "professionId");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(random, "random");
        definition.ifPresent(value -> {
            if (!value.professionId().equals(professionId)) {
                throw new IllegalArgumentException(
                        "Specialization definition does not match profession " + professionId
                );
            }
        });

        VillagerPotentialState assigned = state.assignProfession(professionId, assignmentTime);
        if (assigned.specializationFor(professionId).isPresent()) {
            return assigned;
        }

        SpecializationId specialization = definition
                .map(value -> select(value, random))
                .orElse(SpecializationId.GENERAL);
        return assigned.withSpecialization(professionId, specialization);
    }

    private static SpecializationId select(
            ProfessionSpecializationDefinition definition,
            RandomGenerator random
    ) {
        List<SpecializationId> specializations = definition.namedSpecializations();
        return specializations.isEmpty()
                ? definition.defaultSpecialization()
                : specializations.get(random.nextInt(specializations.size()));
    }
}
