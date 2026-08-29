package org.waterflane.villager_potential.core;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Advances continuous profession skill from elapsed profession time.
 */
public final class SkillProgression {
    private SkillProgression() {
    }

    /**
     * Advances skill at the neutral activity factor for callers without an
     * activity model.
     */
    public static double advance(
            double learnedSkill,
            long elapsedProfessionTime,
            double aptitude,
            SkillProgressionConfig config
    ) {
        return advance(learnedSkill, elapsedProfessionTime, aptitude, 1.0, config);
    }

    /**
     * Advances skill according to time rate x aptitude x professional activity
     * x the current profession-level multiplier. The first two promotions
     * multiply the preceding rate by 1.2; the final two multiply it by 1.5.
     * Activity changes the value of elapsed time; it is never skill by itself.
     */
    public static double advance(
            double learnedSkill,
            long elapsedProfessionTime,
            double aptitude,
            double activityFactor,
            SkillProgressionConfig config
    ) {
        Objects.requireNonNull(config, "config");
        requireFiniteNonNegative("learnedSkill", learnedSkill);
        requireFiniteNonNegative("aptitude", aptitude);
        requireFiniteNonNegative("activityFactor", activityFactor);
        if (elapsedProfessionTime < 0L) {
            throw new IllegalArgumentException("elapsedProfessionTime must not be negative");
        }

        if (!config.enabled()
                || elapsedProfessionTime == 0L
                || aptitude == 0.0
                || activityFactor == 0.0
                || config.progressionRate() == 0.0) {
            return learnedSkill;
        }
        if (learnedSkill >= config.maximumSkill()) {
            return learnedSkill;
        }

        double progressionStart = Math.max(learnedSkill, config.minimumSkill());
        double effectiveAptitude = 1.0
                + (aptitude - 1.0) * config.aptitudeInfluence();
        int professionLevel = config.professionLevelThresholds()
                .levelForSkill(progressionStart);
        double gainedSkill = elapsedProfessionTime
                * config.progressionRate()
                * effectiveAptitude
                * activityFactor
                * professionLevelRateMultiplier(professionLevel);
        if (!Double.isFinite(gainedSkill)
                || gainedSkill >= config.maximumSkill() - progressionStart) {
            return config.maximumSkill();
        }
        return progressionStart + gainedSkill;
    }

    /**
     * Cumulative rate multiplier for the current vanilla profession level.
     * Novice is the neutral baseline; Apprentice and Journeyman each add x1.2,
     * while Expert and Master each add x1.5 relative to the preceding level.
     */
    public static double professionLevelRateMultiplier(int professionLevel) {
        return switch (professionLevel) {
            case 1 -> 1.0;
            case 2 -> 1.2;
            case 3 -> 1.2 * 1.2;
            case 4 -> 1.2 * 1.2 * 1.5;
            case 5 -> 1.2 * 1.2 * 1.5 * 1.5;
            default -> throw new IllegalArgumentException(
                    "professionLevel must be between 1 and 5"
            );
        };
    }

    /**
     * Describes the current level and normalized progress toward the next one.
     */
    public static ProfessionLevelProgress progressTowardNextLevel(
            double learnedSkill,
            SkillProgressionConfig config
    ) {
        Objects.requireNonNull(config, "config");
        requireFiniteNonNegative("learnedSkill", learnedSkill);

        double boundedSkill = clamp(
                learnedSkill,
                config.minimumSkill(),
                config.maximumSkill()
        );
        ProfessionLevelThresholds thresholds = config.professionLevelThresholds();
        int currentLevel = thresholds.levelForSkill(boundedSkill);
        if (currentLevel == ProfessionLevelThresholds.MASTER_LEVEL) {
            return new ProfessionLevelProgress(
                    currentLevel,
                    boundedSkill,
                    1.0,
                    OptionalDouble.empty()
            );
        }

        double currentLevelStart = thresholds.thresholdForLevel(currentLevel);
        double nextLevelSkill = thresholds.thresholdForLevel(currentLevel + 1);
        double levelProgress = Math.max(0.0, (boundedSkill - currentLevelStart)
                / (nextLevelSkill - currentLevelStart));
        return new ProfessionLevelProgress(
                currentLevel,
                boundedSkill,
                levelProgress,
                OptionalDouble.of(nextLevelSkill)
        );
    }

    /**
     * Maps learned skill to one of vanilla's five profession levels.
     */
    public static int vanillaProfessionLevel(
            double learnedSkill,
            SkillProgressionConfig config
    ) {
        Objects.requireNonNull(config, "config");
        requireFiniteNonNegative("learnedSkill", learnedSkill);
        double boundedSkill = clamp(
                learnedSkill,
                config.minimumSkill(),
                config.maximumSkill()
        );
        return config.professionLevelThresholds().levelForSkill(boundedSkill);
    }

    /**
     * Maps learned skill while preserving an existing villager's saved vanilla
     * profession level as the bootstrap floor.
     */
    public static int vanillaProfessionLevel(
            double learnedSkill,
            int currentVanillaLevel,
            SkillProgressionConfig config
    ) {
        Objects.requireNonNull(config, "config");
        requireFiniteNonNegative("learnedSkill", learnedSkill);
        double boundedSkill = clamp(
                learnedSkill,
                config.minimumSkill(),
                config.maximumSkill()
        );
        return config.professionLevelThresholds().levelForSkill(
                boundedSkill,
                currentVanillaLevel
        );
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void requireFiniteNonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
