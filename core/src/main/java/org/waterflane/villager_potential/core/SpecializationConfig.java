package org.waterflane.villager_potential.core;

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

    private static void requireUnit(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between zero and one");
        }
    }
}
