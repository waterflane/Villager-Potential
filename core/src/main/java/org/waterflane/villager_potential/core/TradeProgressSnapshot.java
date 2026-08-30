package org.waterflane.villager_potential.core;

import java.util.Objects;
import java.util.Optional;

/**
 * Loader-neutral snapshot rendered by the profession progress overlay.
 */
public record TradeProgressSnapshot(
        int professionLevel,
        double skill,
        double levelStartSkill,
        double nextLevelSkill,
        double baseSkillPerMinute,
        double skillPerMinute,
        double aptitudeMultiplier,
        double activityMultiplier,
        double activityBaseline,
        double activityMaximum,
        double activityGainPerTrade
) {
    public TradeProgressSnapshot {
        if (professionLevel < ProfessionLevelThresholds.NOVICE_LEVEL
                || professionLevel > ProfessionLevelThresholds.MASTER_LEVEL) {
            throw new IllegalArgumentException("professionLevel must be between 1 and 5");
        }
        requireFiniteNonNegative("skill", skill);
        requireFiniteNonNegative("levelStartSkill", levelStartSkill);
        requireFiniteNonNegative("nextLevelSkill", nextLevelSkill);
        requireFiniteNonNegative("baseSkillPerMinute", baseSkillPerMinute);
        requireFiniteNonNegative("skillPerMinute", skillPerMinute);
        requireFiniteNonNegative("aptitudeMultiplier", aptitudeMultiplier);
        requireFiniteNonNegative("activityMultiplier", activityMultiplier);
        requireFiniteNonNegative("activityBaseline", activityBaseline);
        requireFiniteNonNegative("activityMaximum", activityMaximum);
        requireFiniteNonNegative("activityGainPerTrade", activityGainPerTrade);
    }

    /**
     * Builds a synchronized view when the supplied profession is the villager's
     * active career and all required persisted values exist.
     */
    public static Optional<TradeProgressSnapshot> create(
            VillagerPotentialState state,
            ProfessionId profession,
            int professionLevel,
            long gameTime,
            boolean progressionEligible,
            VillagerPotentialConfig config
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(profession, "profession");
        Objects.requireNonNull(config, "config");
        if (!state.activeProfession().equals(Optional.of(profession))) {
            return Optional.empty();
        }
        ProfessionCareerState career = state.careerFor(profession).orElse(null);
        Double aptitude = state.aptitudes().get(profession);
        if (career == null || aptitude == null) {
            return Optional.empty();
        }

        int level = Math.max(
                ProfessionLevelThresholds.NOVICE_LEVEL,
                Math.min(ProfessionLevelThresholds.MASTER_LEVEL, professionLevel)
        );
        SkillProgressionConfig skillConfig = config.skill();
        ProfessionActivityConfig activityConfig = config.activity();
        ProfessionLevelThresholds thresholds = skillConfig.professionLevelThresholds();
        double levelStart = thresholds.thresholdForLevel(level);
        double nextLevel = level == ProfessionLevelThresholds.MASTER_LEVEL
                ? thresholds.masterSkill()
                : thresholds.thresholdForLevel(level + 1);
        double activity = state.professionActivityFor(profession, gameTime, activityConfig);
        double effectiveAptitude = SkillProgression.effectiveAptitudeMultiplier(
                aptitude,
                skillConfig.aptitudeInfluence()
        );
        double configuredBaseSkillPerMinute = MinecraftTime.TICKS_PER_MINUTE
                * skillConfig.progressionRate();
        boolean progressionActive = skillConfig.enabled()
                && progressionEligible
                && career.learnedSkill() < skillConfig.maximumSkill();
        double baseRate = progressionActive ? configuredBaseSkillPerMinute : 0.0;
        double currentRate = baseRate
                * effectiveAptitude
                * activity
                * SkillProgression.professionLevelRateMultiplier(level);

        return Optional.of(new TradeProgressSnapshot(
                level,
                career.learnedSkill(),
                levelStart,
                nextLevel,
                baseRate,
                currentRate,
                effectiveAptitude,
                activity,
                activityConfig.baseline(),
                activityConfig.maximum(),
                activityConfig.increasePerTradeForLevel(level)
        ));
    }

    public double skillFraction() {
        double span = nextLevelSkill - levelStartSkill;
        if (professionLevel >= ProfessionLevelThresholds.MASTER_LEVEL || span <= 0.0) {
            return 0.0;
        }
        return clamp((skill - levelStartSkill) / span);
    }

    public double activityFraction() {
        double span = activityMaximum - activityBaseline;
        if (span <= 0.0) {
            return 1.0;
        }
        return clamp((activityMultiplier - activityBaseline) / span);
    }

    public double minutesRemaining() {
        double remaining = Math.max(0.0, nextLevelSkill - skill);
        if (remaining == 0.0) {
            return 0.0;
        }
        return skillPerMinute <= 0.0 ? Double.POSITIVE_INFINITY : remaining / skillPerMinute;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static void requireFiniteNonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
