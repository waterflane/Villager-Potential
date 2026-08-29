package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillProgressionTest {
    private static final SkillProgressionConfig CONFIG = new SkillProgressionConfig(
            0.001,
            0.0,
            1.0,
            List.of(0.2, 0.5, 0.8, 1.0)
    );

    @Test
    void equalTimeWithHigherAptitudeGrowsFaster() {
        double ordinarySkill = SkillProgression.advance(0.0, 100L, 1.0, CONFIG);
        double talentedSkill = SkillProgression.advance(0.0, 100L, 1.5, CONFIG);

        assertEquals(0.1, ordinarySkill, 0.000_000_1);
        assertEquals(0.15, talentedSkill, 0.000_000_1);
    }

    @Test
    void zeroElapsedTimeGivesNoProgress() {
        ProfessionCareerState career = new ProfessionCareerState(20L, 0.35, 10L, 10L);

        assertSame(career, career.progressSkill(0L, 2.0, CONFIG));
    }

    @Test
    void configuredProgressionRateControlsGrowth() {
        SkillProgressionConfig fasterConfig = new SkillProgressionConfig(
                0.0025,
                0.0,
                1.0,
                CONFIG.levelThresholds()
        );

        assertEquals(
                0.35,
                SkillProgression.advance(0.1, 100L, 1.0, fasterConfig),
                0.000_000_1
        );
    }

    @Test
    void aptitudeInfluenceCanBlendAptitudeTowardNeutral() {
        SkillProgressionConfig halfInfluence = new SkillProgressionConfig(
                true,
                0.001,
                0.5,
                0.0,
                1.0,
                CONFIG.professionLevelThresholds()
        );

        assertEquals(
                0.125,
                SkillProgression.advance(0.0, 100L, 1.5, halfInfluence),
                0.000_000_1
        );
    }

    @Test
    void effectiveAptitudeMultiplierMatchesTheActualLearningFormula() {
        assertEquals(1.5, SkillProgression.effectiveAptitudeMultiplier(1.5, 1.0));
        assertEquals(1.25, SkillProgression.effectiveAptitudeMultiplier(1.5, 0.5));
        assertEquals(1.0, SkillProgression.effectiveAptitudeMultiplier(0.5, 0.0));
    }

    @Test
    void activityFactorMultipliesTimeBasedProgress() {
        double inactiveSkill = SkillProgression.advance(0.0, 100L, 1.0, 1.0, CONFIG);
        double activeSkill = SkillProgression.advance(0.0, 100L, 1.0, 1.5, CONFIG);

        assertEquals(0.1, inactiveSkill, 0.000_000_1);
        assertEquals(0.15, activeSkill, 0.000_000_1);
        assertTrue(activeSkill > inactiveSkill);
    }

    @Test
    void professionLevelRateMultiplierCompoundsAcrossPromotions() {
        assertEquals(1.0, SkillProgression.professionLevelRateMultiplier(1));
        assertEquals(1.2, SkillProgression.professionLevelRateMultiplier(2));
        assertEquals(1.44, SkillProgression.professionLevelRateMultiplier(3));
        assertEquals(2.16, SkillProgression.professionLevelRateMultiplier(4));
        assertEquals(3.24, SkillProgression.professionLevelRateMultiplier(5));
    }

    @Test
    void currentProfessionLevelMultiplierChangesActualSkillGain() {
        assertEquals(0.1, SkillProgression.advance(0.0, 100L, 1.0, CONFIG), 0.000_000_1);
        assertEquals(0.32, SkillProgression.advance(0.2, 100L, 1.0, CONFIG), 0.000_000_1);
        assertEquals(0.644, SkillProgression.advance(0.5, 100L, 1.0, CONFIG), 0.000_000_1);
        assertEquals(0.8216, SkillProgression.advance(0.8, 10L, 1.0, CONFIG), 0.000_000_1);
    }

    @Test
    void skillBoundsAreEnforced() {
        assertEquals(1.0, SkillProgression.advance(0.9, Long.MAX_VALUE, 2.0, CONFIG));
        assertEquals(0.0, SkillProgression.advance(0.0, 100L, 0.0, CONFIG));
    }

    @Test
    void loweringConfiguredMaximumNeverReducesPersistedSkill() {
        SkillProgressionConfig loweredMaximum = new SkillProgressionConfig(
                true,
                0.001,
                1.0,
                0.0,
                0.8,
                new ProfessionLevelThresholds(0.0, 0.2, 0.4, 0.6, 0.8)
        );

        assertEquals(0.95, SkillProgression.advance(0.95, 100L, 1.0, loweredMaximum));
    }

    @Test
    void reportsContinuousProgressTowardTheNextProfessionLevel() {
        ProfessionLevelProgress progress = SkillProgression.progressTowardNextLevel(0.35, CONFIG);

        assertEquals(2, progress.currentLevel());
        assertEquals(0.5, progress.progressTowardNextLevel(), 0.000_000_1);
        assertEquals(0.5, progress.nextLevelSkill().orElseThrow());
        assertFalse(progress.atMaximumLevel());
    }

    @Test
    void mapsSkillWithoutDemotingAnExistingVanillaLevel() {
        assertEquals(1, SkillProgression.vanillaProfessionLevel(0.0, CONFIG));
        assertEquals(2, SkillProgression.vanillaProfessionLevel(0.2, CONFIG));
        assertEquals(4, SkillProgression.vanillaProfessionLevel(0.0, 4, CONFIG));
        assertEquals(5, SkillProgression.vanillaProfessionLevel(1.0, 2, CONFIG));
    }

    @Test
    void careerProgressAccumulatesTimeAndSkillTogether() {
        ProfessionCareerState career = ProfessionCareerState.firstAssignedAt(10L)
                .progressSkill(100L, 1.5, CONFIG);

        assertEquals(100L, career.accumulatedProfessionTime());
        assertEquals(0.15, career.learnedSkill(), 0.000_000_1);
    }
}
