package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillProgressionConfigTest {
    @Test
    void defaultCurveUsesRequestedWorkdayIntervals() {
        SkillProgressionConfig skill = VillagerPotentialConfig.DEFAULT.skill();
        ProfessionLevelThresholds levels = skill.professionLevelThresholds();
        double ticksPerSkill = 1.0 / skill.progressionRate();

        assertEquals(1.0 / 24_000.0, skill.progressionRate());
        assertEquals(10.5, skill.maximumSkill());
        assertEquals(36_000.0, levels.apprenticeSkill() * ticksPerSkill);
        assertEquals(48_000.0,
                (levels.journeymanSkill() - levels.apprenticeSkill()) * ticksPerSkill);
        assertEquals(72_000.0,
                (levels.expertSkill() - levels.journeymanSkill()) * ticksPerSkill);
        assertEquals(96_000.0,
                (levels.masterSkill() - levels.expertSkill()) * ticksPerSkill);
        assertEquals(0.0, VillagerPotentialConfig.DEFAULT.activity().decayPerTick());
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
