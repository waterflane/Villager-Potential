package org.waterflane.villager_potential.core;

/**
 * Platform-independent parameters for aptitude generation.
 */
public record AptitudeGenerationConfig(
        double minimum,
        double maximum,
        double mean,
        double variance,
        double rareTalentChance
) {
    public AptitudeGenerationConfig {
        requireFinite("minimum", minimum);
        requireFinite("maximum", maximum);
        requireFinite("mean", mean);
        requireFinite("variance", variance);
        requireFinite("rareTalentChance", rareTalentChance);

        if (minimum >= maximum) {
            throw new IllegalArgumentException("minimum must be less than maximum");
        }
        if (mean < minimum || mean > maximum) {
            throw new IllegalArgumentException("mean must be within the configured bounds");
        }
        if (variance < 0.0) {
            throw new IllegalArgumentException("variance must not be negative");
        }
        if (rareTalentChance < 0.0 || rareTalentChance > 1.0) {
            throw new IllegalArgumentException("rareTalentChance must be between zero and one");
        }
    }

    private static void requireFinite(String name, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
