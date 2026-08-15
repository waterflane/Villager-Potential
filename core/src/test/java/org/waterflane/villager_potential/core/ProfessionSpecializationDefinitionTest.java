package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfessionSpecializationDefinitionTest {
    private static final ProfessionId LIBRARIAN = ProfessionId.parse("minecraft:librarian");
    private static final SpecializationId GENERAL =
            SpecializationId.parse("villager_potential:librarian/general");
    private static final SpecializationId ENCHANTER =
            SpecializationId.parse("villager_potential:librarian/enchanter");
    private static final SpecializationId CARTOGRAPHER =
            SpecializationId.parse("villager_potential:librarian/cartographer");

    @Test
    void exposesGeneralAndMultipleNamedOptions() {
        ProfessionSpecializationDefinition definition = definition();

        assertEquals(LIBRARIAN, definition.professionId());
        assertEquals(GENERAL, definition.defaultSpecialization());
        assertEquals(List.of(ENCHANTER, CARTOGRAPHER), definition.namedSpecializations());
        assertEquals(
                List.of(GENERAL, ENCHANTER, CARTOGRAPHER),
                definition.allSpecializations()
        );
        assertTrue(definition.supports(GENERAL));
        assertTrue(definition.supports(ENCHANTER));
        assertFalse(definition.supports(SpecializationId.parse("example:unknown")));
    }

    @Test
    void protectsDefinitionOrderFromExternalMutation() {
        List<SpecializationId> named = new ArrayList<>(List.of(ENCHANTER, CARTOGRAPHER));
        ProfessionSpecializationDefinition definition =
                new ProfessionSpecializationDefinition(LIBRARIAN, GENERAL, named);

        named.clear();

        assertEquals(List.of(ENCHANTER, CARTOGRAPHER), definition.namedSpecializations());
        assertThrows(
                UnsupportedOperationException.class,
                () -> definition.namedSpecializations().add(GENERAL)
        );
    }

    @Test
    void rejectsDuplicateOrAmbiguousOptions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProfessionSpecializationDefinition(
                        LIBRARIAN,
                        GENERAL,
                        List.of(ENCHANTER, ENCHANTER)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProfessionSpecializationDefinition(
                        LIBRARIAN,
                        GENERAL,
                        List.of(GENERAL, ENCHANTER)
                )
        );
    }

    private static ProfessionSpecializationDefinition definition() {
        return new ProfessionSpecializationDefinition(
                LIBRARIAN,
                GENERAL,
                List.of(ENCHANTER, CARTOGRAPHER)
        );
    }
}
