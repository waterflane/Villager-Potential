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

    @Test
    void parsesSharedProfessionOverrideFormat() {
        Map<ProfessionId, Double> overrides = SpecializationConfig.parseStrengthOverrides(
                java.util.List.of("minecraft:librarian=0.75", "minecraft:farmer=0")
        );

        assertEquals(2, overrides.size());
        assertEquals(0.75, overrides.get(LIBRARIAN));
        assertEquals(0.0, overrides.get(FARMER));
    }

    @Test
    void rejectsMalformedDuplicateAndOutOfRangeOverrides() {
        java.util.List<String> invalid = java.util.List.of(
                "not_namespaced=0.5",
                "minecraft:librarian",
                "minecraft:librarian=",
                "=0.5",
                "minecraft:librarian=0.5=0.6",
                "minecraft:librarian=Infinity",
                "minecraft:librarian=-0.5",
                "minecraft:librarian=1.01"
        );
        for (String entry : invalid) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SpecializationConfig.parseStrengthOverrides(java.util.List.of(entry)),
                    "expected rejection of " + entry
            );
        }
        // A missing override list itself is a caller bug, not a config error.
        assertThrows(NullPointerException.class,
                () -> SpecializationConfig.parseStrengthOverrides(null));
        assertThrows(IllegalArgumentException.class,
                () -> SpecializationConfig.parseStrengthOverrides(java.util.Arrays.asList((String) null)));
        assertThrows(
                IllegalArgumentException.class,
                () -> SpecializationConfig.parseStrengthOverrides(java.util.List.of(
                        "minecraft:librarian=0.5",
                        "minecraft:librarian=0.7"
                ))
        );
    }
}
