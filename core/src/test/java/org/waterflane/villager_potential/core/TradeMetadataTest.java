package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeMetadataTest {
    @Test
    void canonicalMetadataIsOrderIndependentAndRoundTrips() {
        Map<String, String> entries = Map.of(
                "minecraft:stored_enchantments", "minecraft:mending@1",
                "minecraft:custom_name", "explorer_map"
        );

        String encoded = TradeMetadata.canonical(entries);

        assertEquals(entries, TradeMetadata.parse(encoded));
        assertTrue(TradeMetadata.isPortable(encoded));
    }

    @Test
    void migratesKnownLegacyComponentsAndDropsDynamicMapIdentity() {
        String legacy = part("minecraft:map_id") + part("3:42")
                + part("minecraft:stored_enchantments") + part("minecraft:mending@1");

        String migrated = TradeMetadata.migrateLegacyComponents(legacy);

        assertEquals(
                Map.of("minecraft:stored_enchantments", "minecraft:mending@1"),
                TradeMetadata.parse(migrated)
        );
    }

    @Test
    void unknownLegacyComponentsBecomeUnstable() {
        String migrated = TradeMetadata.migrateLegacyComponents(
                part("example:custom") + part("opaque")
        );

        assertFalse(TradeMetadata.isPortable(migrated));
    }

    private static String part(String value) {
        return value.length() + ":" + value;
    }
}
