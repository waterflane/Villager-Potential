package org.waterflane.villager_potential.core;

import java.util.List;
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
     * Advances skill according to time rate x aptitude x professional activity.
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

        double boundedSkill = clamp(
                learnedSkill,
                config.minimumSkill(),
                config.maximumSkill()
        );
        if (elapsedProfessionTime == 0L
                || aptitude == 0.0
                || activityFactor == 0.0
                || config.progressionRate() == 0.0) {
            return boundedSkill;
        }

        double gainedSkill = elapsedProfessionTime
                * config.progressionRate()
                * aptitude
                * activityFactor;
        if (!Double.isFinite(gainedSkill)
                || gainedSkill >= config.maximumSkill() - boundedSkill) {
            return config.maximumSkill();
        }
        return clamp(
                boundedSkill + gainedSkill,
                config.minimumSkill(),
                config.maximumSkill()
        );
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
        List<Double> thresholds = config.levelThresholds();
        double currentLevelStart = config.minimumSkill();
        int currentLevel = 1;

        for (double threshold : thresholds) {
            if (boundedSkill < threshold) {
                double levelProgress = (boundedSkill - currentLevelStart)
                        / (threshold - currentLevelStart);
                return new ProfessionLevelProgress(
                        currentLevel,
                        boundedSkill,
                        levelProgress,
                        OptionalDouble.of(threshold)
                );
            }
            currentLevel++;
            currentLevelStart = threshold;
        }

        return new ProfessionLevelProgress(
                currentLevel,
                boundedSkill,
                1.0,
                OptionalDouble.empty()
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
