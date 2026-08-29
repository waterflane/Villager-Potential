package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillProgressionConfigTest {
    @Test
    void defaultCurveMakesMasteryASeparateLongTermGoal() {
        SkillProgressionConfig skill = VillagerPotentialConfig.DEFAULT.skill();
        ProfessionLevelThresholds levels = skill.professionLevelThresholds();
        double journeymanToExpert = levels.expertSkill() - levels.journeymanSkill();
        double expertToMaster = levels.masterSkill() - levels.expertSkill();

        assertEquals(0.00005, skill.progressionRate());
        assertEquals(5.0, skill.maximumSkill());
        assertEquals(0.5, journeymanToExpert);
        assertEquals(4.0, expertToMaster);
        assertTrue(expertToMaster >= journeymanToExpert * 8.0);
    }

    @Test
    void rejectsInvalidRatesBoundsAndThresholds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillProgressionConfig(
                        -0.1,
                        0.0,
                        1.0,
                        List.of(0.2, 0.5, 0.8, 1.0)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillProgressionConfig(
                        0.1,
                        1.0,
                        1.0,
                        List.of(1.2, 1.5, 1.8, 2.0)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillProgressionConfig(
                        0.1,
                        0.0,
                        1.0,
                        List.of(0.2, 0.5, 0.5, 1.0)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillProgressionConfig(
                        0.1,
                        0.0,
                        1.0,
                        List.of(0.2, 0.5, 0.8, 1.1)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillProgressionConfig(0.1, 0.0, 1.0, List.of(0.5))
        );
    }

    @Test
    void protectsThresholdsFromExternalMutation() {
        List<Double> thresholds = new ArrayList<>(List.of(0.2, 0.5, 0.8, 1.0));
        SkillProgressionConfig config = new SkillProgressionConfig(0.1, 0.0, 1.0, thresholds);
        thresholds.set(0, 0.25);

        assertEquals(List.of(0.2, 0.5, 0.8, 1.0), config.levelThresholds());
        assertThrows(UnsupportedOperationException.class, () -> config.levelThresholds().add(1.1));
    }
}
