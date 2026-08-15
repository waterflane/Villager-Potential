package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecializationBiasConfigTest {
    private static final SpecializationBiasConfig CONFIG = new SpecializationBiasConfig(
            0.0,
            1.0,
            0.1,
            0.8,
            2.0
    );

    @Test
    void experiencedVillagersExpressMoreOfTheSpecializationWeight() {
        double lowSkillWeight = CONFIG.weightModifier(4.0, 0.1);
        double highSkillWeight = CONFIG.weightModifier(4.0, 0.9);

        assertTrue(lowSkillWeight > 1.0);
        assertTrue(lowSkillWeight < highSkillWeight);
        assertTrue(highSkillWeight < 4.0);
    }

    @Test
    void configuredMaximumBiasBoundsTheCurveAndEffectiveWeight() {
        assertEquals(0.8, CONFIG.strengthForSkill(1.0));
        assertEquals(0.8, CONFIG.strengthForSkill(100.0));
        assertEquals(3.4, CONFIG.weightModifier(4.0, 100.0), 0.000_000_1);
    }

    @Test
    void curveAlsoWeakensConfiguredCategorySuppressionAtLowSkill() {
        assertEquals(0.9, CONFIG.weightModifier(0.0, 0.0));
        assertEquals(0.2, CONFIG.weightModifier(0.0, 1.0), 0.000_000_1);
    }

    @Test
    void rejectsUnboundedOrInvalidCurves() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SpecializationBiasConfig(0.0, 1.0, 0.0, 1.1, 1.0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SpecializationBiasConfig(0.0, 1.0, 0.0, 1.0, 0.0)
        );
    }
}
