package org.waterflane.villager_potential.core;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * A platform-independent view of skill progress through profession levels.
 */
public record ProfessionLevelProgress(
        int currentLevel,
        double learnedSkill,
        double progressTowardNextLevel,
        OptionalDouble nextLevelSkill
) {
    public ProfessionLevelProgress {
        if (currentLevel < 1) {
            throw new IllegalArgumentException("currentLevel must be positive");
        }
        if (!Double.isFinite(learnedSkill)) {
            throw new IllegalArgumentException("learnedSkill must be finite");
        }
        if (!Double.isFinite(progressTowardNextLevel)
                || progressTowardNextLevel < 0.0
                || progressTowardNextLevel > 1.0) {
            throw new IllegalArgumentException(
                    "progressTowardNextLevel must be between zero and one"
            );
        }
        Objects.requireNonNull(nextLevelSkill, "nextLevelSkill");
        if (nextLevelSkill.isPresent() && nextLevelSkill.getAsDouble() <= learnedSkill) {
            throw new IllegalArgumentException("nextLevelSkill must exceed learnedSkill");
        }
    }

    public boolean atMaximumLevel() {
        return nextLevelSkill.isEmpty();
    }
}
