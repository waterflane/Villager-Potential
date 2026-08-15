package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpecializationIdTest {
    @Test
    void parsesPortableNamespacedIds() {
        SpecializationId general = SpecializationId.parse("villager_potential:general");
        SpecializationId cartographer =
                SpecializationId.parse("example_mod:librarian/cartographer");

        assertEquals("villager_potential", general.namespace());
        assertEquals("general", general.path());
        assertEquals("villager_potential:general", general.toString());
        assertEquals("example_mod:librarian/cartographer", cartographer.toString());
    }

    @Test
    void usesValueEqualityAndHashing() {
        SpecializationId first = SpecializationId.parse("villager_potential:general");
        SpecializationId equal = new SpecializationId("villager_potential", "general");
        SpecializationId different = SpecializationId.parse("villager_potential:enchanter");

        assertEquals(first, equal);
        assertEquals(first.hashCode(), equal.hashCode());
        assertNotEquals(first, different);
    }

    @Test
    void rejectsMalformedIds() {
        assertThrows(IllegalArgumentException.class, () -> SpecializationId.parse("general"));
        assertThrows(IllegalArgumentException.class, () -> SpecializationId.parse(":general"));
        assertThrows(IllegalArgumentException.class, () -> SpecializationId.parse("mod:"));
        assertThrows(IllegalArgumentException.class, () -> SpecializationId.parse("mod:a:b"));
        assertThrows(IllegalArgumentException.class, () -> SpecializationId.parse("Mod:general"));
        assertThrows(IllegalArgumentException.class, () -> SpecializationId.parse("mod:Book Seller"));
        assertThrows(IllegalArgumentException.class, () -> SpecializationId.parse(null));
    }
}
