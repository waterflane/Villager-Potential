package org.waterflane.villager_potential.core;

import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecializationDefinitionParserTest {
    private static final ProfessionId LIBRARIAN = ProfessionId.parse("minecraft:librarian");

    @Test
    void parsesAValidDefinitionWithWeights() {
        ProfessionSpecializationDefinition definition = SpecializationDefinitionParser.parseText(
                "test:librarian",
                """
                {
                  "format_version": 1,
                  "profession": "minecraft:librarian",
                  "general_specialization": "villager_potential:librarian/general",
                  "specializations": [
                    {
                      "id": "villager_potential:librarian/enchanter",
                      "trade_categories": {
                        "villager_potential:enchanted_books": 2.5
                      }
                    }
                  ]
                }
                """
        );

        assertEquals(LIBRARIAN, definition.professionId());
        var enchanter = definition.specialization(
                SpecializationId.parse("villager_potential:librarian/enchanter")
        ).orElseThrow();
        assertEquals(
                2.5,
                enchanter.weightModifierFor(
                        TradeCategoryId.parse("villager_potential:enchanted_books")
                )
        );
        assertEquals(
                1.0,
                enchanter.weightModifierFor(TradeCategoryId.parse("test:unmentioned"))
        );
    }

    @Test
    void supportsGeneralOnlyDefinitions() {
        ProfessionSpecializationDefinition definition = SpecializationDefinitionParser.parseText(
                "test:librarian",
                """
                {
                  "format_version": 1,
                  "profession": "minecraft:librarian",
                  "general_specialization": "test:librarian/general",
                  "specializations": []
                }
                """
        );

        assertTrue(definition.namedSpecializations().isEmpty());
        assertEquals(
                SpecializationId.parse("test:librarian/general"),
                definition.defaultSpecialization()
        );
    }

    @Test
    void rejectsInvalidDocumentsWithSourceContext() {
        assertInvalid("not json", "invalid JSON");
        assertInvalid("""
                {
                  "format_version": 2,
                  "profession": "minecraft:librarian",
                  "general_specialization": "test:general",
                  "specializations": []
                }
                """, "unsupported format_version 2.0");
        assertInvalid("""
                {
                  "format_version": 1,
                  "profession": "Not A Namespaced ID",
                  "general_specialization": "test:general",
                  "specializations": []
                }
                """, "profession");
        assertInvalid("""
                {
                  "format_version": 1,
                  "profession": "minecraft:librarian",
                  "specializations": []
                }
                """, "missing required field 'general_specialization'");
        assertInvalid("""
                {
                  "format_version": 1,
                  "profession": "minecraft:librarian",
                  "general_specialization": "test:general",
                  "specializations": [
                    {
                      "id": "test:enchanter",
                      "trade_categories": {}
                    }
                  ]
                }
                """, "must not be empty");
        assertInvalid("""
                {
                  "format_version": 1,
                  "profession": "minecraft:librarian",
                  "general_specialization": "test:general",
                  "specializations": [
                    {
                      "id": "test:enchanter",
                      "trade_categories": {
                        "test:books": -0.25
                      }
                    }
                  ]
                }
                """, "finite and non-negative");
    }

    private static void assertInvalid(String json, String expectedMessageFragment) {
        JsonParseException exception = assertThrows(
                JsonParseException.class,
                () -> SpecializationDefinitionParser.parseText("test:broken", json)
        );
        assertTrue(exception.getMessage().contains("test:broken"));
        assertTrue(exception.getMessage().contains(expectedMessageFragment));
    }
}
