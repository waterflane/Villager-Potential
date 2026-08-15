package org.waterflane.villager_potential.core;

/**
 * A bounded curve that controls how much of a specialization's configured
 * trade weight is expressed at a given professional skill.
 *
 * <p>Bias strength is an interpolation factor: {@code 0.0} is neutral and
 * {@code 1.0} applies the specialization weight in full. Skill and the
 * resulting strength are both clamped to their configured bounds.</p>
 */
public record SpecializationBiasConfig(
        double minimumSkill,
        double maximumSkill,
        double minimumBiasStrength,
        double maximumBiasStrength,
        double curveExponent
) {
    public SpecializationBiasConfig {
        requireFinite("minimumSkill", minimumSkill);
        requireFinite("maximumSkill", maximumSkill);
        requireFinite("minimumBiasStrength", minimumBiasStrength);
        requireFinite("maximumBiasStrength", maximumBiasStrength);
        requireFinite("curveExponent", curveExponent);

        if (minimumSkill < 0.0 || minimumSkill >= maximumSkill) {
            throw new IllegalArgumentException(
                    "minimumSkill must be non-negative and less than maximumSkill"
            );
        }
        if (minimumBiasStrength < 0.0
                || minimumBiasStrength > maximumBiasStrength
                || maximumBiasStrength > 1.0) {
            throw new IllegalArgumentException(
                    "bias strengths must satisfy 0 <= minimum <= maximum <= 1"
            );
        }
        if (curveExponent <= 0.0) {
            throw new IllegalArgumentException("curveExponent must be positive");
        }
    }

    public double strengthForSkill(double skill) {
        requireFinite("skill", skill);
        double boundedSkill = Math.max(minimumSkill, Math.min(maximumSkill, skill));
        double normalizedSkill = (boundedSkill - minimumSkill) / (maximumSkill - minimumSkill);
        double curvedSkill = Math.pow(normalizedSkill, curveExponent);
        return minimumBiasStrength
                + curvedSkill * (maximumBiasStrength - minimumBiasStrength);
    }

    /**
     * Pulls a specialization weight toward neutral according to professional
     * skill. This changes only a candidate's weight; it does not create one.
     */
    public double weightModifier(double specializationWeight, double skill) {
        if (!Double.isFinite(specializationWeight) || specializationWeight < 0.0) {
            throw new IllegalArgumentException(
                    "specializationWeight must be finite and non-negative"
            );
        }
        return 1.0 + (specializationWeight - 1.0) * strengthForSkill(skill);
    }

    private static void requireFinite(String name, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
