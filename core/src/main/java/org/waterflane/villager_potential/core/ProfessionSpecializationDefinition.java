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
        SpecializationDefinition generalSpecializationDefinition,
        List<SpecializationDefinition> namedSpecializationDefinitions
) {
    public ProfessionSpecializationDefinition {
        Objects.requireNonNull(professionId, "professionId");
        Objects.requireNonNull(generalSpecializationDefinition, "generalSpecializationDefinition");
        Objects.requireNonNull(namedSpecializationDefinitions, "namedSpecializationDefinitions");
        if (namedSpecializationDefinitions.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("namedSpecializationDefinitions must not contain null");
        }
        List<SpecializationId> namedIds = namedSpecializationDefinitions.stream()
                .map(SpecializationDefinition::id)
                .toList();
        if (namedIds.contains(generalSpecializationDefinition.id())) {
            throw new IllegalArgumentException(
                    "General specialization must not also be a named specialization"
            );
        }
        if (new HashSet<>(namedIds).size() != namedIds.size()) {
            throw new IllegalArgumentException("Named specializations must be unique");
        }
        namedSpecializationDefinitions = List.copyOf(namedSpecializationDefinitions);
    }

    public ProfessionSpecializationDefinition(
            ProfessionId professionId,
            SpecializationId generalSpecialization,
            List<SpecializationId> namedSpecializations
    ) {
        this(
                professionId,
                new SpecializationDefinition(generalSpecialization, java.util.Map.of()),
                namedSpecializations.stream()
                        .map(id -> new SpecializationDefinition(id, java.util.Map.of()))
                        .toList()
        );
    }

    public SpecializationId generalSpecialization() {
        return generalSpecializationDefinition.id();
    }

    public List<SpecializationId> namedSpecializations() {
        return namedSpecializationDefinitions.stream()
                .map(SpecializationDefinition::id)
                .toList();
    }

    public boolean supports(SpecializationId specializationId) {
        Objects.requireNonNull(specializationId, "specializationId");
        return generalSpecialization().equals(specializationId)
                || namedSpecializations().contains(specializationId);
    }

    public SpecializationId defaultSpecialization() {
        return generalSpecialization();
    }

    public List<SpecializationId> allSpecializations() {
        java.util.ArrayList<SpecializationId> all =
                new java.util.ArrayList<>(namedSpecializationDefinitions.size() + 1);
        all.add(generalSpecialization());
        all.addAll(namedSpecializations());
        return List.copyOf(all);
    }

    public java.util.Optional<SpecializationDefinition> specialization(SpecializationId specializationId) {
        Objects.requireNonNull(specializationId, "specializationId");
        if (generalSpecialization().equals(specializationId)) {
            return java.util.Optional.of(generalSpecializationDefinition);
        }
        return namedSpecializationDefinitions.stream()
                .filter(definition -> definition.id().equals(specializationId))
                .findFirst();
    }

    /**
     * Resolves the weight modifiers a trade selection should apply for the
     * career's stored specialization. The general specialization means vanilla
     * weights and yields {@link java.util.Optional#empty()}, as does an absent
     * assignment or an id this definition no longer supports.
     */
    public java.util.Optional<SpecializationDefinition> selectionModifiersFor(
            java.util.Optional<SpecializationId> selectedSpecialization
    ) {
        Objects.requireNonNull(selectedSpecialization, "selectedSpecialization");
        if (selectedSpecialization.isEmpty()) {
            return java.util.Optional.empty();
        }
        SpecializationId selected = selectedSpecialization.orElseThrow();
        return generalSpecialization().equals(selected)
                ? java.util.Optional.empty()
                : specialization(selected);
    }
}
