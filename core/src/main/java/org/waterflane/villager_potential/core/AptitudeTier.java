package org.waterflane.villager_potential.core;

import java.util.Objects;

/** Qualitative aptitude derived from the configured generation distribution. */
public enum AptitudeTier {
    POOR,
    AVERAGE,
    PROMISING,
    TALENTED,
    EXCEPTIONAL;

    /**
     * Uses the configured mean and standard deviation for ordinary tiers and
     * the configured rare-talent strength for the exceptional boundary.
     * Bounds cap thresholds so custom distributions retain stable behavior.
     */
    public static AptitudeTier classify(
            double aptitude,
            AptitudeGenerationConfig config
    ) {
        Objects.requireNonNull(config, "config");
        if (!Double.isFinite(aptitude)) {
            throw new IllegalArgumentException("aptitude must be finite");
        }

        double spread = Math.sqrt(config.variance());
        if (spread == 0.0) {
            spread = (config.maximum() - config.minimum()) / 6.0;
        }
        double poorUpper = bounded(config.mean() - spread, config);
        double averageUpper = bounded(config.mean() + spread, config);
        double promisingUpper = bounded(config.mean() + 2.0 * spread, config);
        double exceptionalLower = bounded(
                config.mean() + Math.max(2.0, config.rareTalentStrength()) * spread,
                config
        );

        if (aptitude < poorUpper) {
            return POOR;
        }
        if (aptitude < averageUpper) {
            return AVERAGE;
        }
        if (aptitude < promisingUpper) {
            return PROMISING;
        }
        if (aptitude < exceptionalLower) {
            return TALENTED;
        }
        return EXCEPTIONAL;
    }

    private static double bounded(double value, AptitudeGenerationConfig config) {
        return Math.max(config.minimum(), Math.min(config.maximum(), value));
    }
}
