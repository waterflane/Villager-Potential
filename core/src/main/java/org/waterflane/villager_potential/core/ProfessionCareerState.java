package org.waterflane.villager_potential.core;

/**
 * Persistent progress and assignment history for one profession.
 */
public record ProfessionCareerState(
        long accumulatedProfessionTime,
        double learnedSkill,
        long firstAssignment,
        long latestAssignment
) {
    public ProfessionCareerState {
        if (accumulatedProfessionTime < 0) {
            throw new IllegalArgumentException("accumulatedProfessionTime must not be negative");
        }
        if (!Double.isFinite(learnedSkill) || learnedSkill < 0.0) {
            throw new IllegalArgumentException("learnedSkill must be finite and non-negative");
        }
        if (latestAssignment < firstAssignment) {
            throw new IllegalArgumentException("latestAssignment must not precede firstAssignment");
        }
    }

    public static ProfessionCareerState firstAssignedAt(long assignmentTime) {
        return new ProfessionCareerState(0L, 0.0, assignmentTime, assignmentTime);
    }

    public ProfessionCareerState reassignedAt(long assignmentTime) {
        if (assignmentTime < latestAssignment) {
            throw new IllegalArgumentException("assignmentTime must not precede latestAssignment");
        }
        return new ProfessionCareerState(
                accumulatedProfessionTime,
                learnedSkill,
                firstAssignment,
                assignmentTime
        );
    }

    public ProfessionCareerState withLearnedSkill(double skill) {
        return new ProfessionCareerState(
                accumulatedProfessionTime,
                skill,
                firstAssignment,
                latestAssignment
        );
    }
}
