package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TradeKeyTest {
    @Test
    void equivalentPortableRepresentationsAreEqual() {
        TradeKey first = new TradeKey.Offer(
                new TradeKey.Item("minecraft:emerald", 12),
                Optional.of(new TradeKey.Item("minecraft:book", 1)),
                new TradeKey.Item(
                        "minecraft:enchanted_book",
                        1,
                        "minecraft:stored_enchantments=minecraft:mending@1"
                )
        );
        TradeKey second = new TradeKey.Offer(
                new TradeKey.Item("minecraft:emerald", 12),
                Optional.of(new TradeKey.Item("minecraft:book", 1)),
                new TradeKey.Item(
                        "minecraft:enchanted_book",
                        1,
                        "minecraft:stored_enchantments=minecraft:mending@1"
                )
        );

        assertEquals(first, second);
        assertNotEquals(
                first,
                new TradeKey.Fallback("merchant-offer:example.UnknownOffer")
        );
    }
}
