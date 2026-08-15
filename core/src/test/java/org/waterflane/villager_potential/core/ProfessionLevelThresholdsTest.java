package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfessionLevelThresholdsTest {
    private static final ProfessionLevelThresholds THRESHOLDS =
            new ProfessionLevelThresholds(0.0, 0.2, 0.5, 0.8, 1.0);

    @Test
    void mapsEveryThresholdAndBoundaryToItsVanillaLevel() {
        assertEquals(1, THRESHOLDS.levelForSkill(0.0));
        assertEquals(1, THRESHOLDS.levelForSkill(Math.nextDown(0.2)));
        assertEquals(2, THRESHOLDS.levelForSkill(0.2));
        assertEquals(2, THRESHOLDS.levelForSkill(Math.nextDown(0.5)));
        assertEquals(3, THRESHOLDS.levelForSkill(0.5));
        assertEquals(3, THRESHOLDS.levelForSkill(Math.nextDown(0.8)));
        assertEquals(4, THRESHOLDS.levelForSkill(0.8));
        assertEquals(4, THRESHOLDS.levelForSkill(Math.nextDown(1.0)));
        assertEquals(5, THRESHOLDS.levelForSkill(1.0));
        assertEquals(5, THRESHOLDS.levelForSkill(2.0));
    }

    @Test
    void rejectsThresholdsThatAreNotStrictlyOrdered() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProfessionLevelThresholds(0.0, 0.2, 0.2, 0.8, 1.0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProfessionLevelThresholds(0.0, 0.5, 0.4, 0.8, 1.0)
        );
    }

    @Test
    void existingVanillaLevelIsABootstrapFloor() {
        assertEquals(4, THRESHOLDS.levelForSkill(0.0, 4));
        assertEquals(4, THRESHOLDS.levelForSkill(0.8, 2));
        assertEquals(5, THRESHOLDS.levelForSkill(1.0, 5));
    }
}
