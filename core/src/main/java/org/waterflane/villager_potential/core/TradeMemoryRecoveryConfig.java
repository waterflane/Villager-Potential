package org.waterflane.villager_potential.core;

/** Bounded recovery rules for reroll strategies backed by trade memory. */
public record TradeMemoryRecoveryConfig(
        long weightedPenaltyRecoveryTime,
        double minimumCandidateWeight,
        long exhaustRecoveryTime,
        long cyclicResetTime,
        long rareTradeRecoveryTime
) {
    public TradeMemoryRecoveryConfig {
        if (weightedPenaltyRecoveryTime < 1L) {
            throw new IllegalArgumentException("weightedPenaltyRecoveryTime must be positive");
        }
        if (!Double.isFinite(minimumCandidateWeight) || minimumCandidateWeight < 0.0) {
            throw new IllegalArgumentException(
                    "minimumCandidateWeight must be finite and non-negative"
            );
        }
        if (exhaustRecoveryTime < 1L) {
            throw new IllegalArgumentException("exhaustRecoveryTime must be positive");
        }
        if (cyclicResetTime < 1L) {
            throw new IllegalArgumentException("cyclicResetTime must be positive");
        }
        if (rareTradeRecoveryTime < 0L) {
            throw new IllegalArgumentException("rareTradeRecoveryTime must not be negative");
        }
    }
}
