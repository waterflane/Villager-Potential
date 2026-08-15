package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillProgressionConfigTest {
    @Test
    void rejectsInvalidRatesBoundsAndThresholds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillProgressionConfig(-0.1, 0.0, 1.0, List.of(0.5))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillProgressionConfig(0.1, 1.0, 1.0, List.of())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillProgressionConfig(0.1, 0.0, 1.0, List.of(0.5, 0.5))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillProgressionConfig(0.1, 0.0, 1.0, List.of(1.1))
        );
    }

    @Test
    void protectsThresholdsFromExternalMutation() {
        List<Double> thresholds = new ArrayList<>(List.of(0.5));
        SkillProgressionConfig config = new SkillProgressionConfig(0.1, 0.0, 1.0, thresholds);
        thresholds.set(0, 0.75);

        assertEquals(List.of(0.5), config.levelThresholds());
        assertThrows(UnsupportedOperationException.class, () -> config.levelThresholds().add(0.75));
    }
}
