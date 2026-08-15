package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfessionIdTest {
    @Test
    void parsesValidNamespacedIds() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        ProfessionId modded = ProfessionId.parse("example_mod:trades/master-librarian");

        assertEquals("minecraft", librarian.namespace());
        assertEquals("librarian", librarian.path());
        assertEquals("minecraft:librarian", librarian.toString());
        assertEquals("example_mod:trades/master-librarian", modded.toString());
    }

    @Test
    void usesValueEqualityAndHashing() {
        ProfessionId first = ProfessionId.parse("minecraft:librarian");
        ProfessionId equal = new ProfessionId("minecraft", "librarian");
        ProfessionId different = ProfessionId.parse("minecraft:farmer");

        assertEquals(first, equal);
        assertEquals(first.hashCode(), equal.hashCode());
        assertNotEquals(first, different);
    }

    @Test
    void rejectsIdsWithoutBothNamespacedComponents() {
        assertThrows(IllegalArgumentException.class, () -> ProfessionId.parse("librarian"));
        assertThrows(IllegalArgumentException.class, () -> ProfessionId.parse(":librarian"));
        assertThrows(IllegalArgumentException.class, () -> ProfessionId.parse("minecraft:"));
        assertThrows(IllegalArgumentException.class, () -> ProfessionId.parse("minecraft:jobs:librarian"));
    }

    @Test
    void rejectsInvalidCharactersAndNull() {
        assertThrows(IllegalArgumentException.class, () -> ProfessionId.parse("Minecraft:librarian"));
        assertThrows(IllegalArgumentException.class, () -> ProfessionId.parse("minecraft:Library Clerk"));
        assertThrows(IllegalArgumentException.class, () -> ProfessionId.parse(null));
        assertThrows(IllegalArgumentException.class, () -> new ProfessionId(null, "librarian"));
        assertThrows(IllegalArgumentException.class, () -> new ProfessionId("minecraft", null));
    }
}
