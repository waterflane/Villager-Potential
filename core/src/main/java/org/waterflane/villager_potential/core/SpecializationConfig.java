package org.waterflane.villager_potential.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Loader-neutral controls layered over datapack specialization definitions. */
public record SpecializationConfig(
        boolean enabled,
        double globalStrength,
        double minimumBiasStrength,
        double maximumBiasStrength,
        double curveExponent,
        Map<ProfessionId, Double> professionStrengthOverrides
) {
    public SpecializationConfig {
        requireUnit("globalStrength", globalStrength);
        requireUnit("minimumBiasStrength", minimumBiasStrength);
        requireUnit("maximumBiasStrength", maximumBiasStrength);
        if (minimumBiasStrength > maximumBiasStrength) {
            throw new IllegalArgumentException(
                    "minimumBiasStrength must not exceed maximumBiasStrength"
            );
        }
        if (!Double.isFinite(curveExponent) || curveExponent <= 0.0) {
            throw new IllegalArgumentException("curveExponent must be finite and positive");
        }
        Objects.requireNonNull(professionStrengthOverrides, "professionStrengthOverrides");
        professionStrengthOverrides.forEach((profession, strength) -> {
            Objects.requireNonNull(profession, "override profession");
            if (strength == null) {
                throw new IllegalArgumentException("override strength must not be null");
            }
            requireUnit("override strength", strength);
        });
        professionStrengthOverrides = Map.copyOf(professionStrengthOverrides);
    }

    public double strengthFor(ProfessionId profession) {
        Objects.requireNonNull(profession, "profession");
        if (!enabled) {
            return 0.0;
        }
        return professionStrengthOverrides.getOrDefault(profession, globalStrength);
    }

    public SpecializationBiasConfig biasFor(
            ProfessionId profession,
            double minimumSkill,
            double maximumSkill
    ) {
        double strength = strengthFor(profession);
        return new SpecializationBiasConfig(
                minimumSkill,
                maximumSkill,
                minimumBiasStrength * strength,
                maximumBiasStrength * strength,
                curveExponent
        );
    }

    /**
     * Parses the shared {@code namespaced_profession_id=value} override
     * format used by every loader's config integration. Entries must carry a
     * single {@code '='}, a parseable profession id, and a finite strength in
     * [0, 1]; duplicates are rejected. Error messages are part of the format
     * contract because they surface verbatim in config reload failures.
     */
    public static Map<ProfessionId, Double> parseStrengthOverrides(List<String> entries) {
        Objects.requireNonNull(entries, "entries");
        Map<ProfessionId, Double> overrides = new LinkedHashMap<>();
        for (int index = 0; index < entries.size(); index++) {
            String entry = entries.get(index);
            if (entry == null) {
                throw new IllegalArgumentException(
                        "specializations.professionStrengthOverrides[" + index + "] must be a string"
                );
            }
            int separator = entry.lastIndexOf('=');
            if (separator <= 0 || separator != entry.indexOf('=')
                    || separator == entry.length() - 1) {
                throw new IllegalArgumentException(
                        "specializations.professionStrengthOverrides[" + index
                                + "] must use namespaced_profession_id=value"
                );
            }
            ProfessionId profession;
            double strength;
            try {
                profession = ProfessionId.parse(entry.substring(0, separator));
                strength = Double.parseDouble(entry.substring(separator + 1));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Invalid specialization profession override '" + entry + "': "
                                + exception.getMessage(),
                        exception
                );
            }
            if (!Double.isFinite(strength) || strength < 0.0 || strength > 1.0) {
                throw new IllegalArgumentException(
                        "Specialization strength for " + profession
                                + " must be finite and between zero and one"
                );
            }
            if (overrides.putIfAbsent(profession, strength) != null) {
                throw new IllegalArgumentException(
                        "Duplicate specialization profession override for " + profession
                );
            }
        }
        return overrides;
    }

    private static void requireUnit(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between zero and one");
        }
    }
}
