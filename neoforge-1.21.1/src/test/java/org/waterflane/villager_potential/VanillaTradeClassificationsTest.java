package org.waterflane.villager_potential;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.TradeCategoryId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaTradeClassificationsTest {
    @Test
    void classifiesRepresentativeTradeFromEveryVanillaProfession() {
        Map<VillagerProfession, Representative> representatives = Map.ofEntries(
                Map.entry(VillagerProfession.FARMER, representative(1, 0, VanillaTradeClassifications.CROPS)),
                Map.entry(VillagerProfession.FISHERMAN, representative(3, 1, VanillaTradeClassifications.FISHING_SUPPLIES)),
                Map.entry(VillagerProfession.SHEPHERD, representative(2, 0, VanillaTradeClassifications.DYES)),
                Map.entry(VillagerProfession.FLETCHER, representative(5, 2, VanillaTradeClassifications.ARROWS)),
                Map.entry(VillagerProfession.LIBRARIAN, representative(1, 2, VanillaTradeClassifications.ORDINARY_BOOKS)),
                Map.entry(VillagerProfession.CARTOGRAPHER, representative(2, 1, VanillaTradeClassifications.MAPS)),
                Map.entry(VillagerProfession.CLERIC, representative(5, 1, VanillaTradeClassifications.ALCHEMY)),
                Map.entry(VillagerProfession.ARMORER, representative(4, 0, VanillaTradeClassifications.ARMOR)),
                Map.entry(VillagerProfession.WEAPONSMITH, representative(5, 0, VanillaTradeClassifications.WEAPONS)),
                Map.entry(VillagerProfession.TOOLSMITH, representative(5, 0, VanillaTradeClassifications.TOOLS)),
                Map.entry(VillagerProfession.BUTCHER, representative(1, 0, VanillaTradeClassifications.RAW_MEAT)),
                Map.entry(VillagerProfession.LEATHERWORKER, representative(1, 1, VanillaTradeClassifications.LEATHER_GOODS)),
                Map.entry(VillagerProfession.MASON, representative(4, 1, VanillaTradeClassifications.TERRACOTTA))
        );

        representatives.forEach((profession, representative) -> {
            VillagerTrades.ItemListing listing = VillagerTrades.TRADES.get(profession)
                    .get(representative.level())[representative.index()];
            assertEquals(
                    representative.category(),
                    VanillaTradeClassifications.classify(profession, representative.level(), listing),
                    profession.toString()
            );
        });
    }

    @Test
    void everyNormalVanillaCandidateIsRegisteredWithoutChangingThePool() {
        VillagerTrades.TRADES.forEach((profession, levels) -> levels.int2ObjectEntrySet().forEach(entry -> {
            for (VillagerTrades.ItemListing listing : entry.getValue()) {
                assertTrue(
                        VanillaTradeClassifications.isBuiltIn(profession, entry.getIntKey(), listing),
                        profession + " level " + entry.getIntKey()
                );
            }
        }));
    }

    @Test
    void everyTradeRebalanceCandidateIsRegistered() {
        VillagerTrades.EXPERIMENTAL_TRADES.forEach((profession, levels) ->
                levels.int2ObjectEntrySet().forEach(entry -> {
                    for (VillagerTrades.ItemListing listing : entry.getValue()) {
                        assertTrue(
                                VanillaTradeClassifications.isBuiltIn(profession, entry.getIntKey(), listing),
                                "experimental " + profession + " level " + entry.getIntKey()
                        );
                    }
                })
        );
    }

    @Test
    void enchantedBookFactoriesKeepTheirSemanticCategoryAndDelegate() {
        VillagerTrades.ItemListing normalBook = VillagerTrades.TRADES.get(VillagerProfession.LIBRARIAN).get(1)[1];
        VillagerTrades.ItemListing experimentalBook =
                VillagerTrades.EXPERIMENTAL_TRADES.get(VillagerProfession.LIBRARIAN).get(5)[0];

        ClassifiedItemListing wrapped = assertInstanceOf(
                ClassifiedItemListing.class,
                VanillaTradeClassifications.wrap(VillagerProfession.LIBRARIAN, 1, normalBook)
        );
        assertSame(normalBook, wrapped.delegate());
        assertEquals(VanillaTradeClassifications.ENCHANTED_BOOKS, wrapped.category());
        assertEquals(
                VanillaTradeClassifications.ENCHANTED_BOOKS,
                VanillaTradeClassifications.classify(VillagerProfession.LIBRARIAN, 5, experimentalBook)
        );
    }

    @Test
    void wrappingRetainsCandidateCountOrderAndDelegateIdentity() {
        VillagerTrades.ItemListing[] source = VillagerTrades.TRADES.get(VillagerProfession.LIBRARIAN).get(1);
        ArrayList<VillagerTrades.ItemListing> level = new ArrayList<>(List.of(source));
        Int2ObjectOpenHashMap<List<VillagerTrades.ItemListing>> pools = new Int2ObjectOpenHashMap<>();
        pools.put(1, level);

        VanillaTradeClassifications.wrapPool(VillagerProfession.LIBRARIAN, pools);

        assertEquals(source.length, level.size());
        for (int index = 0; index < source.length; index++) {
            ClassifiedItemListing classified = assertInstanceOf(ClassifiedItemListing.class, level.get(index));
            assertSame(source[index], classified.delegate());
        }
    }

    @Test
    void unknownCandidateFallsBackToGeneral() {
        VillagerTrades.ItemListing unknown = (entity, random) -> null;

        assertEquals(
                VanillaTradeClassifications.GENERAL,
                VanillaTradeClassifications.classify(VillagerProfession.FARMER, 1, unknown)
        );
        ClassifiedItemListing wrapped = assertInstanceOf(
                ClassifiedItemListing.class,
                VanillaTradeClassifications.wrap(VillagerProfession.FARMER, 1, unknown)
        );
        assertSame(unknown, wrapped.delegate());
        assertEquals(VanillaTradeClassifications.GENERAL, wrapped.category());
    }

    private static Representative representative(int level, int index, TradeCategoryId category) {
        return new Representative(level, index, category);
    }

    private record Representative(int level, int index, TradeCategoryId category) {
    }
}
