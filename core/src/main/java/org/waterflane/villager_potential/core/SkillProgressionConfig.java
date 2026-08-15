package org.waterflane.villager_potential.core;

import java.util.List;
import java.util.Objects;

/**
 * Platform-independent parameters for profession skill progression.
 *
 * <p>The progression rate is measured in skill per unit of profession time at
 * an aptitude of {@code 1.0}. Level thresholds contain the skill required to
 * enter each level after the first.</p>
 */
public record SkillProgressionConfig(
        double progressionRate,
        double minimumSkill,
        double maximumSkill,
        List<Double> levelThresholds
) {
    public SkillProgressionConfig {
        requireFinite("progressionRate", progressionRate);
        requireFinite("minimumSkill", minimumSkill);
        requireFinite("maximumSkill", maximumSkill);
        if (progressionRate < 0.0) {
            throw new IllegalArgumentException("progressionRate must not be negative");
        }
        if (minimumSkill < 0.0) {
            throw new IllegalArgumentException("minimumSkill must not be negative");
        }
        if (minimumSkill >= maximumSkill) {
            throw new IllegalArgumentException("minimumSkill must be less than maximumSkill");
        }

        Objects.requireNonNull(levelThresholds, "levelThresholds");
        levelThresholds = List.copyOf(levelThresholds);
        double previousThreshold = minimumSkill;
        for (Double threshold : levelThresholds) {
            if (threshold == null || !Double.isFinite(threshold)) {
                throw new IllegalArgumentException("level thresholds must be finite");
            }
            if (threshold <= previousThreshold || threshold > maximumSkill) {
                throw new IllegalArgumentException(
                        "level thresholds must be strictly increasing and within the skill bounds"
                );
            }
            previousThreshold = threshold;
        }
    }

    private static void requireFinite(String name, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
