package org.waterflane.villager_potential.core;

import java.util.List;
import java.util.Objects;

/**
 * Platform-independent parameters for profession skill progression.
 *
 * <p>The progression rate is measured in skill per unit of profession time at
 * an aptitude of {@code 1.0}. Named level thresholds contain the skill required
 * to enter each vanilla profession level.</p>
 */
public record SkillProgressionConfig(
        boolean enabled,
        double progressionRate,
        double aptitudeInfluence,
        double minimumSkill,
        double maximumSkill,
        ProfessionLevelThresholds thresholds
) {
    private static final int VANILLA_LEVEL_TRANSITION_COUNT = 4;

    public SkillProgressionConfig(
            double progressionRate,
            double minimumSkill,
            double maximumSkill,
            List<Double> levelThresholds
    ) {
        this(
                true,
                progressionRate,
                1.0,
                minimumSkill,
                maximumSkill,
                thresholds(minimumSkill, levelThresholds)
        );
    }

    public SkillProgressionConfig {
        requireFinite("progressionRate", progressionRate);
        requireFinite("aptitudeInfluence", aptitudeInfluence);
        requireFinite("minimumSkill", minimumSkill);
        requireFinite("maximumSkill", maximumSkill);
        if (progressionRate < 0.0) {
            throw new IllegalArgumentException("progressionRate must not be negative");
        }
        if (aptitudeInfluence < 0.0 || aptitudeInfluence > 1.0) {
            throw new IllegalArgumentException("aptitudeInfluence must be between zero and one");
        }
        if (minimumSkill < 0.0) {
            throw new IllegalArgumentException("minimumSkill must not be negative");
        }
        if (minimumSkill >= maximumSkill) {
            throw new IllegalArgumentException("minimumSkill must be less than maximumSkill");
        }

        Objects.requireNonNull(thresholds, "thresholds");
        if (thresholds.noviceSkill() < minimumSkill
                || thresholds.masterSkill() > maximumSkill) {
            throw new IllegalArgumentException("level thresholds must be within the skill bounds");
        }
    }

    /**
     * Returns the named vanilla profession thresholds represented by this
     * progression configuration.
     */
    public ProfessionLevelThresholds professionLevelThresholds() {
        return thresholds;
    }

    /** Compatibility view of the four transitions after novice. */
    public List<Double> levelThresholds() {
        return List.of(
                thresholds.apprenticeSkill(),
                thresholds.journeymanSkill(),
                thresholds.expertSkill(),
                thresholds.masterSkill()
        );
    }

    private static ProfessionLevelThresholds thresholds(
            double minimumSkill,
            List<Double> levelThresholds
    ) {
        Objects.requireNonNull(levelThresholds, "levelThresholds");
        List<Double> copied = List.copyOf(levelThresholds);
        if (copied.size() != VANILLA_LEVEL_TRANSITION_COUNT) {
            throw new IllegalArgumentException(
                    "levelThresholds must define apprentice, journeyman, expert and master"
            );
        }
        return new ProfessionLevelThresholds(
                minimumSkill,
                copied.get(0),
                copied.get(1),
                copied.get(2),
                copied.get(3)
        );
    }

    private static void requireFinite(String name, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
