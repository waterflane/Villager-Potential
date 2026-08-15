package org.waterflane.villager_potential.core;

/**
 * Parameters for the normalized, profession-wide activity created by trades.
 */
public record ProfessionActivityConfig(
        double baseline,
        double maximum,
        double increasePerTrade,
        double decayPerTick
) {
    public ProfessionActivityConfig {
        if (!Double.isFinite(baseline)
                || baseline < ProfessionActivityState.MINIMUM_SCORE
                || baseline > ProfessionActivityState.MAXIMUM_SCORE) {
            throw new IllegalArgumentException("baseline must be within the activity bounds");
        }
        if (!Double.isFinite(maximum)
                || maximum < baseline
                || maximum > ProfessionActivityState.MAXIMUM_SCORE) {
            throw new IllegalArgumentException("maximum must be between baseline and 1.0");
        }
        if (!Double.isFinite(increasePerTrade) || increasePerTrade <= 0.0) {
            throw new IllegalArgumentException("increasePerTrade must be finite and positive");
        }
        if (!Double.isFinite(decayPerTick) || decayPerTick <= 0.0) {
            throw new IllegalArgumentException("decayPerTick must be finite and positive");
        }
    }
}
