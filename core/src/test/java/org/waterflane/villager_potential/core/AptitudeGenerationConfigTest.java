package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AptitudeGenerationConfigTest {
    @Test
    void rejectsInvalidParameters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AptitudeGenerationConfig(1.0, 1.0, 1.0, 0.1, 0.01)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AptitudeGenerationConfig(0.0, 1.0, 2.0, 0.1, 0.01)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AptitudeGenerationConfig(0.0, 1.0, 0.5, -0.1, 0.01)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AptitudeGenerationConfig(0.0, 1.0, 0.5, 0.1, 1.01)
        );
    }
}
