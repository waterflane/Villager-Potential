package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeKeyTest {
    @Test
    void equivalentPortableRepresentationsAreEqual() {
        TradeKey first = new TradeKey.Offer(
                new TradeKey.Item("minecraft:emerald", 12),
                Optional.of(new TradeKey.Item("minecraft:book", 1)),
                new TradeKey.Item(
                        "minecraft:enchanted_book",
                        1,
                        TradeMetadata.canonical(Map.of(
                                "minecraft:stored_enchantments", "minecraft:mending@1"
                        ))
                )
        );
        TradeKey second = new TradeKey.Offer(
                new TradeKey.Item("minecraft:emerald", 12),
                Optional.of(new TradeKey.Item("minecraft:book", 1)),
                new TradeKey.Item(
                        "minecraft:enchanted_book",
                        1,
                        TradeMetadata.canonical(Map.of(
                                "minecraft:stored_enchantments", "minecraft:mending@1"
                        ))
                )
        );

        assertEquals(first, second);
        assertNotEquals(
                first,
                new TradeKey.Fallback("merchant-offer:example.UnknownOffer")
        );
    }

    @Test
    void onlyStructuredOffersWithoutFallbackMarkersAreStable() {
        TradeKey.Offer plain = new TradeKey.Offer(
                new TradeKey.Item("minecraft:emerald", 1),
                new TradeKey.Item("minecraft:book", 12)
        );
        TradeKey.Offer withComponents = new TradeKey.Offer(
                new TradeKey.Item("minecraft:emerald", 1),
                new TradeKey.Item(
                        "minecraft:enchanted_book",
                        1,
                        TradeMetadata.canonical(Map.of(
                                "minecraft:stored_enchantments", "minecraft:mending@1"
                        ))
                )
        );
        TradeKey fallback = new TradeKey.Fallback("merchant-offer:example.UnknownOffer");
        TradeKey unregisteredItem = new TradeKey.Offer(
                new TradeKey.Item("unregistered:example.CustomItem", 1),
                new TradeKey.Item("minecraft:book", 1)
        );
        TradeKey fallbackComponent = new TradeKey.Offer(
                new TradeKey.Item("minecraft:emerald", 1),
                new TradeKey.Item("minecraft:netherite_axe", 1, "fallback:example.CustomComponent")
        );

        assertTrue(TradeKey.isStable(plain));
        assertTrue(TradeKey.isStable(withComponents));
        assertFalse(TradeKey.isStable(fallback));
        assertFalse(TradeKey.isStable(unregisteredItem));
        assertFalse(TradeKey.isStable(fallbackComponent));
        assertThrows(NullPointerException.class, () -> TradeKey.isStable(null));
    }

    @Test
    void sameShapeComparesItemIdentityAndIgnoresCountsAndComponents() {
        TradeKey.Offer paperForBook = new TradeKey.Offer(
                new TradeKey.Item("minecraft:paper", 24),
                Optional.of(new TradeKey.Item("minecraft:book", 3)),
                new TradeKey.Item(
                        "minecraft:enchanted_book",
                        1,
                        "minecraft:stored_enchantments=minecraft:mending@1"
                )
        );
        TradeKey.Offer sameShapeDifferentCounts = new TradeKey.Offer(
                new TradeKey.Item("minecraft:paper", 5),
                Optional.of(new TradeKey.Item("minecraft:book", 1)),
                new TradeKey.Item("minecraft:enchanted_book", 1, "other=encoding")
        );
        TradeKey.Offer differentResult = new TradeKey.Offer(
                new TradeKey.Item("minecraft:paper", 24),
                new TradeKey.Item("minecraft:glass_bottle", 3)
        );
        TradeKey.Offer missingCostB = new TradeKey.Offer(
                new TradeKey.Item("minecraft:paper", 24),
                new TradeKey.Item("minecraft:enchanted_book", 1)
        );
        TradeKey.Fallback fallback = new TradeKey.Fallback("merchant-offer:x");

        assertTrue(TradeKey.sameShape(paperForBook, sameShapeDifferentCounts));
        assertFalse(TradeKey.sameShape(paperForBook, differentResult));
        assertFalse(TradeKey.sameShape(paperForBook, missingCostB));
        assertTrue(TradeKey.sameShape(fallback, new TradeKey.Fallback("merchant-offer:y")));
        assertFalse(TradeKey.sameShape(fallback, paperForBook));
    }
}
