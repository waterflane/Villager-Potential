package org.waterflane.villager_potential.core;

/**
 * Bounds and tuning for the profession-wide progression multiplier created by trades.
 */
public record ProfessionActivityConfig(
        double minimum,
        double baseline,
        double maximum,
        double increasePerTrade,
        double decayPerTick
) {
    public ProfessionActivityConfig {
        if (!Double.isFinite(minimum) || minimum < 0.0) {
            throw new IllegalArgumentException("minimum must be finite and non-negative");
        }
        if (!Double.isFinite(baseline) || baseline <= 0.0 || baseline < minimum) {
            throw new IllegalArgumentException(
                    "baseline must be finite, positive, and at least the minimum"
            );
        }
        if (!Double.isFinite(maximum) || maximum < baseline) {
            throw new IllegalArgumentException("maximum must be finite and at least the baseline");
        }
        if (!Double.isFinite(increasePerTrade) || increasePerTrade <= 0.0) {
            throw new IllegalArgumentException("increasePerTrade must be finite and positive");
        }
        if (!Double.isFinite(decayPerTick) || decayPerTick <= 0.0) {
            throw new IllegalArgumentException("decayPerTick must be finite and positive");
        }
    }
}
