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

    public ProfessionCareerState accumulateProfessionTime(long elapsedTicks) {
        if (elapsedTicks < 0) {
            throw new IllegalArgumentException("elapsedTicks must not be negative");
        }
        if (elapsedTicks == 0 || accumulatedProfessionTime == Long.MAX_VALUE) {
            return this;
        }

        long accumulatedTime = Long.MAX_VALUE - accumulatedProfessionTime < elapsedTicks
                ? Long.MAX_VALUE
                : accumulatedProfessionTime + elapsedTicks;
        return new ProfessionCareerState(
                accumulatedTime,
                learnedSkill,
                firstAssignment,
                latestAssignment
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

    /**
     * Accumulates profession time and derives skill growth from that same span.
     */
    public ProfessionCareerState progressSkill(
            long elapsedProfessionTime,
            double aptitude,
            SkillProgressionConfig config
    ) {
        return progressSkill(elapsedProfessionTime, aptitude, 1.0, config);
    }

    public ProfessionCareerState progressSkill(
            long elapsedProfessionTime,
            double aptitude,
            double activityFactor,
            SkillProgressionConfig config
    ) {
        ProfessionCareerState accumulated = accumulateProfessionTime(elapsedProfessionTime);
        double progressedSkill = SkillProgression.advance(
                learnedSkill,
                elapsedProfessionTime,
                aptitude,
                activityFactor,
                config
        );
        if (accumulated == this && progressedSkill == learnedSkill) {
            return this;
        }
        return new ProfessionCareerState(
                accumulated.accumulatedProfessionTime,
                progressedSkill,
                firstAssignment,
                latestAssignment
        );
    }

    public ProfessionLevelProgress levelProgress(SkillProgressionConfig config) {
        return SkillProgression.progressTowardNextLevel(learnedSkill, config);
    }

    public int vanillaProfessionLevel(
            int currentVanillaLevel,
            SkillProgressionConfig config
    ) {
        return SkillProgression.vanillaProfessionLevel(
                learnedSkill,
                currentVanillaLevel,
                config
        );
    }
}
