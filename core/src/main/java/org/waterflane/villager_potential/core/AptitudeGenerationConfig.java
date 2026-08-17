package org.waterflane.villager_potential.core;

/**
 * Platform-independent parameters for aptitude generation.
 */
public record AptitudeGenerationConfig(
        boolean enabled,
        double minimum,
        double maximum,
        double mean,
        double variance,
        RareTalentConfig rareTalents
) {
    public AptitudeGenerationConfig(
            double minimum,
            double maximum,
            double mean,
            double variance,
            double rareTalentChance
    ) {
        this(
                true,
                minimum,
                maximum,
                mean,
                variance,
                new RareTalentConfig(true, rareTalentChance, 3.0)
        );
    }

    public AptitudeGenerationConfig(
            double minimum,
            double maximum,
            double mean,
            double variance,
            double rareTalentChance,
            double rareTalentStrength
    ) {
        this(
                true,
                minimum,
                maximum,
                mean,
                variance,
                new RareTalentConfig(true, rareTalentChance, rareTalentStrength)
        );
    }

    public AptitudeGenerationConfig {
        requireFinite("minimum", minimum);
        requireFinite("maximum", maximum);
        requireFinite("mean", mean);
        requireFinite("variance", variance);
        if (rareTalents == null) {
            throw new NullPointerException("rareTalents");
        }

        if (minimum >= maximum) {
            throw new IllegalArgumentException("minimum must be less than maximum");
        }
        if (mean < minimum || mean > maximum) {
            throw new IllegalArgumentException("mean must be within the configured bounds");
        }
        if (variance < 0.0) {
            throw new IllegalArgumentException("variance must not be negative");
        }
    }

    public double rareTalentChance() {
        return rareTalents.chance();
    }

    public double rareTalentStrength() {
        return rareTalents.strength();
    }

    private static void requireFinite(String name, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
