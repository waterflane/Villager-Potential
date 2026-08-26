package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    @Test
    void exposesValidatedTradeCategoryWeightModifiers() {
        TradeCategoryId books = TradeCategoryId.parse("villager_potential:books");
        SpecializationDefinition enchanter = new SpecializationDefinition(
                ENCHANTER,
                Map.of(books, 2.0)
        );
        ProfessionSpecializationDefinition definition = new ProfessionSpecializationDefinition(
                LIBRARIAN,
                new SpecializationDefinition(GENERAL, Map.of()),
                List.of(enchanter)
        );

        assertEquals(enchanter, definition.specialization(ENCHANTER).orElseThrow());
        assertEquals(2.0, enchanter.weightModifierFor(books));
        assertEquals(
                1.0,
                enchanter.weightModifierFor(TradeCategoryId.parse("example:other"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SpecializationDefinition(ENCHANTER, Map.of(books, -1.0))
        );
    }

    @Test
    void selectionModifiersAreNeutralForGeneralAndAbsentAssignments() {
        java.util.Optional<SpecializationDefinition> modifiers = definition()
                .selectionModifiersFor(java.util.Optional.of(ENCHANTER));
        assertTrue(modifiers.isPresent());
        assertEquals(ENCHANTER, modifiers.orElseThrow().id());

        assertTrue(definition()
                .selectionModifiersFor(java.util.Optional.of(GENERAL)).isEmpty());
        assertTrue(definition()
                .selectionModifiersFor(java.util.Optional.empty()).isEmpty());
        // Unsupported ids resolve to nothing instead of throwing.
        assertTrue(definition()
                .selectionModifiersFor(
                        java.util.Optional.of(SpecializationId.parse("example:missing")))
                .isEmpty());
    }

    private static ProfessionSpecializationDefinition definition() {
        return new ProfessionSpecializationDefinition(
                LIBRARIAN,
                GENERAL,
                List.of(ENCHANTER, CARTOGRAPHER)
        );
    }
}
