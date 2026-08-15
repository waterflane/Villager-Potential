package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VillagerPotentialStateTest {
    @Test
    void createsDefaultStateAtCurrentSchemaVersion() {
        assertEquals(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                VillagerPotentialState.createDefault().schemaVersion()
        );
    }

    @Test
    void requiresAPositiveSchemaVersion() {
        assertThrows(IllegalArgumentException.class, () -> new VillagerPotentialState(0));
    }

    @Test
    void usesValueEquality() {
        VillagerPotentialState state = VillagerPotentialState.createDefault();

        assertEquals(state, new VillagerPotentialState(state.schemaVersion()));
    }
}
