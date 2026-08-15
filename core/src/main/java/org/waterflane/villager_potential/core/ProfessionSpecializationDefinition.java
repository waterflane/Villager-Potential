package org.waterflane.villager_potential.core;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * The specialization options made available by one profession.
 *
 * <p>The general specialization is an explicit, portable option rather than
 * the absence of a specialization. Named specializations are kept in
 * definition order for future presentation and selection policies.</p>
 */
public record ProfessionSpecializationDefinition(
        ProfessionId professionId,
        SpecializationId generalSpecialization,
        List<SpecializationId> namedSpecializations
) {
    public ProfessionSpecializationDefinition {
        Objects.requireNonNull(professionId, "professionId");
        Objects.requireNonNull(generalSpecialization, "generalSpecialization");
        Objects.requireNonNull(namedSpecializations, "namedSpecializations");
        if (namedSpecializations.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("namedSpecializations must not contain null");
        }
        if (namedSpecializations.contains(generalSpecialization)) {
            throw new IllegalArgumentException(
                    "General specialization must not also be a named specialization"
            );
        }
        if (new HashSet<>(namedSpecializations).size() != namedSpecializations.size()) {
            throw new IllegalArgumentException("Named specializations must be unique");
        }
        namedSpecializations = List.copyOf(namedSpecializations);
    }

    public boolean supports(SpecializationId specializationId) {
        Objects.requireNonNull(specializationId, "specializationId");
        return generalSpecialization.equals(specializationId)
                || namedSpecializations.contains(specializationId);
    }

    public SpecializationId defaultSpecialization() {
        return generalSpecialization;
    }

    public List<SpecializationId> allSpecializations() {
        java.util.ArrayList<SpecializationId> all =
                new java.util.ArrayList<>(namedSpecializations.size() + 1);
        all.add(generalSpecialization);
        all.addAll(namedSpecializations);
        return List.copyOf(all);
    }
}
