package org.waterflane.villager_potential.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Portable trade-weight composition and sampling without replacement. */
public final class TradeSelectionResolver {
    private TradeSelectionResolver() {
    }

    /**
     * Normalized platform description of one candidate. Modifiers are applied
     * in this order: vanilla weight, skill-strengthened specialization, trade
     * memory, then the configured override.
     */
    public record Candidate(
            double vanillaWeight,
            double specializationModifier,
            double configuredOverride,
            TradeHistory history,
            boolean rareProtected
    ) {
    }

    public record Rules(
            double skill,
            SpecializationBiasConfig specializationBias,
            TradePaletteRerollStrategy strategy,
            long professionTime,
            double seenTradeWeightMultiplier,
            TradeMemoryRecoveryConfig memoryRecovery,
            long cycleFloor,
            boolean resetCycle
    ) {
        public Rules {
            Objects.requireNonNull(specializationBias, "specializationBias");
            Objects.requireNonNull(strategy, "strategy");
            Objects.requireNonNull(memoryRecovery, "memoryRecovery");
        }
    }

    public interface SelectionRandom {
        int nextInt(int bound);

        double nextDouble();
    }

    /** Returns the selected candidate index, or {@code -1} when none is eligible. */
    public static int selectIndex(
            List<Candidate> candidates,
            Rules rules,
            SelectionRandom random
    ) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(random, "random");
        if (candidates.isEmpty()) {
            return -1;
        }

        List<Double> weights = new ArrayList<>(candidates.size());
        double maximumWeight = 0.0;
        double firstPositiveWeight = 0.0;
        boolean uniform = true;
        int positiveCount = 0;
        for (Candidate candidate : candidates) {
            double weight = resolvedWeight(Objects.requireNonNull(candidate, "candidate"), rules);
            weights.add(weight);
            maximumWeight = Math.max(maximumWeight, weight);
            if (weight > 0.0) {
                if (positiveCount == 0) {
                    firstPositiveWeight = weight;
                } else if (weight != firstPositiveWeight) {
                    uniform = false;
                }
                positiveCount++;
            }
        }
        if (positiveCount == 0) {
            return -1;
        }

        // Preserve vanilla's RNG call and uniform removal semantics when all
        // eligible candidates have the same effective weight.
        if (uniform) {
            int selected = random.nextInt(positiveCount);
            for (int index = 0; index < weights.size(); index++) {
                if (weights.get(index) > 0.0 && selected-- == 0) {
                    return index;
                }
            }
        }

        double normalizedTotal = 0.0;
        for (double weight : weights) {
            normalizedTotal += weight / maximumWeight;
        }
        double target = random.nextDouble() * normalizedTotal;
        double cumulativeWeight = 0.0;
        int lastPositiveIndex = -1;
        for (int index = 0; index < weights.size(); index++) {
            double weight = weights.get(index);
            if (weight == 0.0) {
                continue;
            }
            lastPositiveIndex = index;
            cumulativeWeight += weight / maximumWeight;
            if (target < cumulativeWeight) {
                return index;
            }
        }
        return lastPositiveIndex;
    }

    public static double resolvedWeight(Candidate candidate, Rules rules) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(rules, "rules");
        double vanillaWeight = validModifier(candidate.vanillaWeight());
        double specializationModifier = candidate.specializationModifier();
        double configuredOverride = validModifier(candidate.configuredOverride());
        if (vanillaWeight == 0.0 || !Double.isFinite(specializationModifier)
                || specializationModifier < 0.0 || configuredOverride == 0.0) {
            return 0.0;
        }

        double strengthenedSpecialization = rules.specializationBias()
                .weightModifier(specializationModifier, rules.skill());
        double specializedWeight = safeMultiply(vanillaWeight, strengthenedSpecialization);
        if (specializedWeight == 0.0) {
            return 0.0;
        }
        double memoryWeight = TradeMemoryRecovery.candidateWeight(
                rules.strategy(),
                specializedWeight,
                candidate.history(),
                rules.professionTime(),
                rules.seenTradeWeightMultiplier(),
                rules.memoryRecovery(),
                candidate.rareProtected(),
                rules.cycleFloor(),
                rules.resetCycle()
        );
        return safeMultiply(memoryWeight, configuredOverride);
    }

    private static double validModifier(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 0.0;
    }

    private static double safeMultiply(double first, double second) {
        if (!Double.isFinite(first) || first <= 0.0
                || !Double.isFinite(second) || second <= 0.0) {
            return 0.0;
        }
        if (first > Double.MAX_VALUE / second) {
            return Double.MAX_VALUE;
        }
        return first * second;
    }
}
