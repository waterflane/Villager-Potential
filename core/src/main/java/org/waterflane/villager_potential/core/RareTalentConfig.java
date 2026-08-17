package org.waterflane.villager_potential.core;

/** Platform-independent tuning for the exceptional upper aptitude tail. */
public record RareTalentConfig(
        boolean enabled,
        double chance,
        double strength
) {
    public RareTalentConfig {
        if (!Double.isFinite(chance) || chance < 0.0 || chance > 1.0) {
            throw new IllegalArgumentException("chance must be between zero and one");
        }
        if (!Double.isFinite(strength) || strength < 0.0) {
            throw new IllegalArgumentException("strength must be finite and non-negative");
        }
    }
}
