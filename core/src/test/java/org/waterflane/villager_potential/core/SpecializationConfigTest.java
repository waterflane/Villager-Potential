package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpecializationConfigTest {
    private static final ProfessionId LIBRARIAN = ProfessionId.parse("minecraft:librarian");
    private static final ProfessionId FARMER = ProfessionId.parse("minecraft:farmer");

    @Test
    void professionOverrideReplacesGlobalStrengthWithoutChangingCurveShape() {
        SpecializationConfig config = new SpecializationConfig(
                true,
                0.8,
                0.1,
                1.0,
                2.0,
                Map.of(LIBRARIAN, 0.5)
        );

        SpecializationBiasConfig librarian = config.biasFor(LIBRARIAN, 0.0, 1.0);
        SpecializationBiasConfig farmer = config.biasFor(FARMER, 0.0, 1.0);
        assertEquals(0.05, librarian.minimumBiasStrength(), 0.000_000_1);
        assertEquals(0.5, librarian.maximumBiasStrength(), 0.000_000_1);
        assertEquals(0.08, farmer.minimumBiasStrength(), 0.000_000_1);
        assertEquals(0.8, farmer.maximumBiasStrength(), 0.000_000_1);
        assertEquals(2.0, librarian.curveExponent());
    }

    @Test
    void disabledSpecializationsAreNeutralWithoutDiscardingOverrides() {
        SpecializationConfig config = new SpecializationConfig(
                false,
                1.0,
                0.1,
                1.0,
                2.0,
                Map.of(LIBRARIAN, 0.5)
        );

        assertEquals(1.0, config.biasFor(LIBRARIAN, 0.0, 1.0)
                .weightModifier(4.0, 1.0));
        assertEquals(0.5, config.professionStrengthOverrides().get(LIBRARIAN));
    }

    @Test
    void rejectsInvalidStrengthBounds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SpecializationConfig(true, 1.1, 0.1, 1.0, 2.0, Map.of())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SpecializationConfig(true, 1.0, 0.8, 0.4, 2.0, Map.of())
        );
    }
}
