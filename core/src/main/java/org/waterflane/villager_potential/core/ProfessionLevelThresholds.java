package org.waterflane.villager_potential.core;

/**
 * Skill thresholds for the five vanilla villager profession levels.
 *
 * <p>A threshold is inclusive: reaching it grants the corresponding level.
 * Skill below the novice threshold still maps to novice because vanilla has no
 * profession level below one.</p>
 */
public record ProfessionLevelThresholds(
        double noviceSkill,
        double apprenticeSkill,
        double journeymanSkill,
        double expertSkill,
        double masterSkill
) {
    public static final int NOVICE_LEVEL = 1;
    public static final int APPRENTICE_LEVEL = 2;
    public static final int JOURNEYMAN_LEVEL = 3;
    public static final int EXPERT_LEVEL = 4;
    public static final int MASTER_LEVEL = 5;

    public ProfessionLevelThresholds {
        requireFiniteNonNegative("noviceSkill", noviceSkill);
        requireFiniteNonNegative("apprenticeSkill", apprenticeSkill);
        requireFiniteNonNegative("journeymanSkill", journeymanSkill);
        requireFiniteNonNegative("expertSkill", expertSkill);
        requireFiniteNonNegative("masterSkill", masterSkill);
        if (noviceSkill >= apprenticeSkill
                || apprenticeSkill >= journeymanSkill
                || journeymanSkill >= expertSkill
                || expertSkill >= masterSkill) {
            throw new IllegalArgumentException(
                    "profession level thresholds must be strictly increasing"
            );
        }
    }

    /**
     * Returns the vanilla profession level earned by the supplied skill.
     */
    public int levelForSkill(double learnedSkill) {
        requireFiniteNonNegative("learnedSkill", learnedSkill);
        if (learnedSkill >= masterSkill) {
            return MASTER_LEVEL;
        }
        if (learnedSkill >= expertSkill) {
            return EXPERT_LEVEL;
        }
        if (learnedSkill >= journeymanSkill) {
            return JOURNEYMAN_LEVEL;
        }
        if (learnedSkill >= apprenticeSkill) {
            return APPRENTICE_LEVEL;
        }
        return NOVICE_LEVEL;
    }

    /**
     * Maps skill without demoting a villager that already had a higher vanilla
     * level when Potential was introduced to the world.
     */
    public int levelForSkill(double learnedSkill, int currentVanillaLevel) {
        requireVanillaLevel(currentVanillaLevel);
        return Math.max(currentVanillaLevel, levelForSkill(learnedSkill));
    }

    public double thresholdForLevel(int vanillaLevel) {
        return switch (vanillaLevel) {
            case NOVICE_LEVEL -> noviceSkill;
            case APPRENTICE_LEVEL -> apprenticeSkill;
            case JOURNEYMAN_LEVEL -> journeymanSkill;
            case EXPERT_LEVEL -> expertSkill;
            case MASTER_LEVEL -> masterSkill;
            default -> throw new IllegalArgumentException(
                    "vanillaLevel must be between novice and master"
            );
        };
    }

    private static void requireVanillaLevel(int vanillaLevel) {
        if (vanillaLevel < NOVICE_LEVEL || vanillaLevel > MASTER_LEVEL) {
            throw new IllegalArgumentException(
                    "currentVanillaLevel must be between novice and master"
            );
        }
    }

    private static void requireFiniteNonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
