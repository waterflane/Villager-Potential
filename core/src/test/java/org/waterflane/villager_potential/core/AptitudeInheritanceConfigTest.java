package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AptitudeInheritanceConfigTest {
    @Test
    void rejectsInvalidParameters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AptitudeInheritanceConfig(-0.1, 0.1, 0.01)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AptitudeInheritanceConfig(0.5, 0.6, 0.01)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AptitudeInheritanceConfig(0.5, 0.1, -0.01)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AptitudeInheritanceConfig(Double.NaN, 0.1, 0.01)
        );
    }
}
