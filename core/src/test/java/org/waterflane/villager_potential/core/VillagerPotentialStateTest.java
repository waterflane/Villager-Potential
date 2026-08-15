package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertEquals(state, new VillagerPotentialState(state.schemaVersion(), Map.of()));
    }

    @Test
    void storesIndependentAptitudesPerProfession() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        ProfessionId engineer = ProfessionId.parse("example_mod:engineer");

        VillagerPotentialState state = VillagerPotentialState.createDefault()
                .withAptitude(librarian, 0.75)
                .withAptitude(engineer, 1.25);

        assertEquals(0.75, state.aptitudeFor(librarian).orElseThrow());
        assertEquals(1.25, state.aptitudeFor(engineer).orElseThrow());
    }

    @Test
    void missingProfessionHasNoGeneratedAptitude() {
        VillagerPotentialState state = VillagerPotentialState.createDefault();

        assertFalse(state.aptitudeFor(ProfessionId.parse("minecraft:farmer")).isPresent());
        assertTrue(state.aptitudes().isEmpty());
    }

    @Test
    void protectsStoredAptitudesFromExternalMutation() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        Map<ProfessionId, Double> aptitudes = new HashMap<>();
        aptitudes.put(librarian, 0.75);

        VillagerPotentialState state = new VillagerPotentialState(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                aptitudes
        );
        aptitudes.put(librarian, 1.5);

        assertEquals(0.75, state.aptitudeFor(librarian).orElseThrow());
        assertThrows(
                UnsupportedOperationException.class,
                () -> state.aptitudes().put(librarian, 1.5)
        );
    }

    @Test
    void currentSchemaDoesNotRequireMigration() {
        VillagerPotentialState state = VillagerPotentialState.createDefault();

        assertEquals(state, VillagerPotentialState.migrate(state.schemaVersion()));
    }

    @Test
    void migratesSyntheticVersionZero() {
        assertEquals(VillagerPotentialState.createDefault(), VillagerPotentialState.migrate(0));
    }

    @Test
    void migratesVersionOneWithoutGeneratingAptitudes() {
        assertEquals(VillagerPotentialState.createDefault(), VillagerPotentialState.migrate(1));
    }

    @Test
    void rejectsUnknownNewerSchemaVersion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> VillagerPotentialState.migrate(VillagerPotentialState.CURRENT_SCHEMA_VERSION + 1)
        );
    }
}
