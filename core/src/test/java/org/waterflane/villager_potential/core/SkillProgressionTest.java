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
    void activityFactorMultipliesTimeBasedProgress() {
        double inactiveSkill = SkillProgression.advance(0.0, 100L, 1.0, 1.0, CONFIG);
        double activeSkill = SkillProgression.advance(0.0, 100L, 1.0, 1.5, CONFIG);

        assertEquals(0.1, inactiveSkill, 0.000_000_1);
        assertEquals(0.15, activeSkill, 0.000_000_1);
        assertTrue(activeSkill > inactiveSkill);
    }

    @Test
    void skillBoundsAreEnforced() {
        assertEquals(1.0, SkillProgression.advance(0.9, Long.MAX_VALUE, 2.0, CONFIG));
        assertEquals(0.0, SkillProgression.advance(0.0, 100L, 0.0, CONFIG));
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
    void careerProgressAccumulatesTimeAndSkillTogether() {
        ProfessionCareerState career = ProfessionCareerState.firstAssignedAt(10L)
                .progressSkill(100L, 1.5, CONFIG);

        assertEquals(100L, career.accumulatedProfessionTime());
        assertEquals(0.15, career.learnedSkill(), 0.000_000_1);
    }
}
