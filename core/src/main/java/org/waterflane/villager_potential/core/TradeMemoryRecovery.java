package org.waterflane.villager_potential.core;

import java.util.Collection;
import java.util.Objects;

/** Pure trade-memory recovery calculations using accumulated profession time. */
public final class TradeMemoryRecovery {
    private TradeMemoryRecovery() {
    }

    public static double candidateWeight(
            TradePaletteRerollStrategy strategy,
            double baselineWeight,
            TradeHistory history,
            long professionTime,
            double seenTradeWeightMultiplier,
            TradeMemoryRecoveryConfig config,
            boolean rareProtected,
            long cycleFloor,
            boolean resetCycle
    ) {
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(config, "config");
        validateInputs(baselineWeight, professionTime, seenTradeWeightMultiplier);

        return switch (strategy) {
            case WEIGHTED_MEMORY -> weightedMemoryWeight(
                    baselineWeight,
                    history,
                    professionTime,
                    seenTradeWeightMultiplier,
                    config,
                    rareProtected
            );
            case EXHAUST -> exhaustEligible(
                    history,
                    professionTime,
                    config,
                    rareProtected
            ) ? baselineWeight : 0.0;
            case CYCLIC -> resetCycle || effectiveCyclicCount(
                    history,
                    professionTime,
                    config,
                    rareProtected
            ) == cycleFloor ? baselineWeight : 0.0;
            case PERSISTENT, VANILLA -> baselineWeight;
        };
    }

    public static long effectiveCyclicCount(
            TradeHistory history,
            long professionTime,
            TradeMemoryRecoveryConfig config,
            boolean rareProtected
    ) {
        Objects.requireNonNull(config, "config");
        validateProfessionTime(professionTime);
        if (history == null || history.timesSeen() == 0L) {
            return 0L;
        }
        return rareProtected && recovered(
                history,
                professionTime,
                config.rareTradeRecoveryTime()
        ) ? 0L : history.timesSeen();
    }

    /** A cycle resets only after every candidate has been idle for the configured span. */
    public static boolean shouldResetCycle(
            Collection<TradeHistory> candidateHistories,
            long professionTime,
            TradeMemoryRecoveryConfig config
    ) {
        Objects.requireNonNull(candidateHistories, "candidateHistories");
        Objects.requireNonNull(config, "config");
        validateProfessionTime(professionTime);
        long newestSeen = Long.MIN_VALUE;
        boolean hasSeenCandidate = false;
        for (TradeHistory history : candidateHistories) {
            if (history == null || history.timesSeen() == 0L) {
                continue;
            }
            hasSeenCandidate = true;
            if (history.lastSeen().isPresent()) {
                newestSeen = Math.max(newestSeen, history.lastSeen().getAsLong());
            }
        }
        return hasSeenCandidate && (newestSeen == Long.MIN_VALUE || elapsedAtLeast(
                professionTime,
                newestSeen,
                config.cyclicResetTime()
        ));
    }

    private static double weightedMemoryWeight(
            double baselineWeight,
            TradeHistory history,
            long professionTime,
            double seenTradeWeightMultiplier,
            TradeMemoryRecoveryConfig config,
            boolean rareProtected
    ) {
        if (history == null || history.timesSeen() == 0L || history.lastSeen().isEmpty()) {
            return baselineWeight;
        }
        long recoveryTime = recoveryTime(
                config.weightedPenaltyRecoveryTime(),
                config.rareTradeRecoveryTime(),
                rareProtected
        );
        long elapsed = elapsed(professionTime, history.lastSeen().getAsLong());
        double recovery = Math.min(1.0, (double) elapsed / recoveryTime);
        double multiplier = seenTradeWeightMultiplier
                + (1.0 - seenTradeWeightMultiplier) * recovery;
        double minimum = Math.min(baselineWeight, config.minimumCandidateWeight());
        return Math.max(minimum, baselineWeight * multiplier);
    }

    private static boolean exhaustEligible(
            TradeHistory history,
            long professionTime,
            TradeMemoryRecoveryConfig config,
            boolean rareProtected
    ) {
        if (history == null || history.timesSeen() == 0L || history.lastSeen().isEmpty()) {
            return true;
        }
        long recoveryTime = recoveryTime(
                config.exhaustRecoveryTime(),
                config.rareTradeRecoveryTime(),
                rareProtected
        );
        return elapsedAtLeast(professionTime, history.lastSeen().getAsLong(), recoveryTime);
    }

    private static boolean recovered(
            TradeHistory history,
            long professionTime,
            long recoveryTime
    ) {
        return recoveryTime > 0L
                && (history.lastSeen().isEmpty() || elapsedAtLeast(
                professionTime,
                history.lastSeen().getAsLong(),
                recoveryTime
        ));
    }

    private static long recoveryTime(long normal, long rare, boolean rareProtected) {
        return rareProtected && rare > 0L ? Math.min(normal, rare) : normal;
    }

    private static boolean elapsedAtLeast(long current, long previous, long duration) {
        return current < previous || current - previous >= duration;
    }

    private static long elapsed(long current, long previous) {
        return current >= previous ? current - previous : Long.MAX_VALUE;
    }

    private static void validateInputs(
            double baselineWeight,
            long professionTime,
            double seenTradeWeightMultiplier
    ) {
        if (!Double.isFinite(baselineWeight) || baselineWeight < 0.0) {
            throw new IllegalArgumentException("baselineWeight must be finite and non-negative");
        }
        validateProfessionTime(professionTime);
        if (!Double.isFinite(seenTradeWeightMultiplier)
                || seenTradeWeightMultiplier < 0.0
                || seenTradeWeightMultiplier > 1.0) {
            throw new IllegalArgumentException(
                    "seenTradeWeightMultiplier must be finite and between zero and one"
            );
        }
    }

    private static void validateProfessionTime(long professionTime) {
        if (professionTime < 0L) {
            throw new IllegalArgumentException("professionTime must not be negative");
        }
    }
}
