package org.waterflane.villager_potential;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import org.waterflane.villager_potential.core.TradeCategoryId;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stable specialization categories for the Minecraft 1.20.1 vanilla pools.
 *
 * <p>Entries are identified from the real {@link VillagerTrades} arrays by
 * profession, level, and listing identity. This avoids materializing random or
 * world-dependent offers merely to inspect them. An unknown listing is always
 * classified as {@link #GENERAL}.
 */
public final class VanillaTradeClassifications {
    public static final TradeCategoryId GENERAL = category("general");
    public static final TradeCategoryId CROPS = category("crops");
    public static final TradeCategoryId PREPARED_FOOD = category("prepared_food");
    public static final TradeCategoryId FISH = category("fish");
    public static final TradeCategoryId FISHING_SUPPLIES = category("fishing_supplies");
    public static final TradeCategoryId WOOL = category("wool");
    public static final TradeCategoryId DYES = category("dyes");
    public static final TradeCategoryId DECOR = category("decor");
    public static final TradeCategoryId ARCHERY_SUPPLIES = category("archery_supplies");
    public static final TradeCategoryId ARROWS = category("arrows");
    public static final TradeCategoryId BOWS = category("bows");
    public static final TradeCategoryId CROSSBOWS = category("crossbows");
    public static final TradeCategoryId ENCHANTED_BOOKS = category("enchanted_books");
    public static final TradeCategoryId ORDINARY_BOOKS = category("ordinary_books");
    public static final TradeCategoryId MAPS = category("maps");
    public static final TradeCategoryId CARTOGRAPHY_SUPPLIES = category("cartography_supplies");
    public static final TradeCategoryId ALCHEMY = category("alchemy");
    public static final TradeCategoryId ARMOR = category("armor");
    public static final TradeCategoryId SMITHING_MATERIALS = category("smithing_materials");
    public static final TradeCategoryId WEAPONS = category("weapons");
    public static final TradeCategoryId TOOLS = category("tools");
    public static final TradeCategoryId RAW_MEAT = category("raw_meat");
    public static final TradeCategoryId COOKED_FOOD = category("cooked_food");
    public static final TradeCategoryId LEATHER_MATERIALS = category("leather_materials");
    public static final TradeCategoryId LEATHER_GOODS = category("leather_goods");
    public static final TradeCategoryId STONEWORK = category("stonework");
    public static final TradeCategoryId TERRACOTTA = category("terracotta");
    public static final TradeCategoryId QUARTZ = category("quartz");

    private static final Map<PoolKey, IdentityHashMap<VillagerTrades.ItemListing, TradeCategoryId>> BUILT_INS =
            buildClassifications();

    private VanillaTradeClassifications() {
    }

    /** Forces the vanilla identity snapshot to be taken during mod startup. */
    public static void bootstrap() {
        // Accessing this class initializes BUILT_INS.
    }

    public static TradeCategoryId classify(
            VillagerProfession profession,
            int level,
            VillagerTrades.ItemListing listing
    ) {
        Objects.requireNonNull(profession, "profession");
        Objects.requireNonNull(listing, "listing");
        if (listing instanceof ClassifiedItemListing classified) {
            return classified.category();
        }

        Map<VillagerTrades.ItemListing, TradeCategoryId> levelClassifications =
                BUILT_INS.get(new PoolKey(profession, level));
        return levelClassifications == null
                ? GENERAL
                : levelClassifications.getOrDefault(listing, GENERAL);
    }

    public static VillagerTrades.ItemListing wrap(
            VillagerProfession profession,
            int level,
            VillagerTrades.ItemListing listing
    ) {
        Objects.requireNonNull(listing, "listing");
        if (listing instanceof ClassifiedItemListing) {
            return listing;
        }
        return new ClassifiedItemListing(listing, classify(profession, level, listing));
    }

    static void wrapPool(
            VillagerProfession profession,
            Int2ObjectMap<List<VillagerTrades.ItemListing>> trades
    ) {
        trades.int2ObjectEntrySet().forEach(entry -> {
            int level = entry.getIntKey();
            entry.getValue().replaceAll(listing -> wrap(profession, level, listing));
        });
    }

    static boolean isBuiltIn(
            VillagerProfession profession,
            int level,
            VillagerTrades.ItemListing listing
    ) {
        Map<VillagerTrades.ItemListing, TradeCategoryId> classifications =
                BUILT_INS.get(new PoolKey(profession, level));
        return classifications != null && classifications.containsKey(listing);
    }

    private static Map<PoolKey, IdentityHashMap<VillagerTrades.ItemListing, TradeCategoryId>> buildClassifications() {
        Map<PoolKey, IdentityHashMap<VillagerTrades.ItemListing, TradeCategoryId>> result = new java.util.HashMap<>();
        registerNormal(result);
        return Map.copyOf(result);
    }

    private static void registerNormal(
            Map<PoolKey, IdentityHashMap<VillagerTrades.ItemListing, TradeCategoryId>> result
    ) {
        register(result, VillagerTrades.TRADES, VillagerProfession.FARMER, Map.of(
                1, sequence(CROPS, 4, PREPARED_FOOD, 1),
                2, sequence(CROPS, 1, PREPARED_FOOD, 2),
                3, list(PREPARED_FOOD, CROPS),
                4, repeat(PREPARED_FOOD, 7),
                5, repeat(PREPARED_FOOD, 2)
        ));
        register(result, VillagerTrades.TRADES, VillagerProfession.FISHERMAN, Map.of(
                1, sequence(FISHING_SUPPLIES, 2, FISH, 2),
                2, list(FISH, FISH, FISHING_SUPPLIES),
                3, list(FISH, FISHING_SUPPLIES),
                4, list(FISH),
                5, list(FISH, FISHING_SUPPLIES)
        ));
        register(result, VillagerTrades.TRADES, VillagerProfession.SHEPHERD, Map.of(
                1, sequence(WOOL, 5),
                2, sequence(DYES, 5, WOOL, 16, DECOR, 16),
                3, sequence(DYES, 5, DECOR, 16),
                4, sequence(DYES, 6, DECOR, 16),
                5, list(DECOR)
        ));
        register(result, VillagerTrades.TRADES, VillagerProfession.FLETCHER, Map.of(
                1, list(ARCHERY_SUPPLIES, ARROWS, ARCHERY_SUPPLIES),
                2, list(ARCHERY_SUPPLIES, BOWS),
                3, list(ARCHERY_SUPPLIES, CROSSBOWS),
                4, list(ARCHERY_SUPPLIES, BOWS),
                5, list(ARCHERY_SUPPLIES, CROSSBOWS, ARROWS)
        ));
        register(result, VillagerTrades.TRADES, VillagerProfession.LIBRARIAN, Map.of(
                1, list(ORDINARY_BOOKS, ENCHANTED_BOOKS, ORDINARY_BOOKS),
                2, list(ORDINARY_BOOKS, ENCHANTED_BOOKS, GENERAL),
                3, list(ORDINARY_BOOKS, ENCHANTED_BOOKS, GENERAL),
                4, list(ORDINARY_BOOKS, ENCHANTED_BOOKS, GENERAL, GENERAL),
                5, list(GENERAL)
        ));
        register(result, VillagerTrades.TRADES, VillagerProfession.CARTOGRAPHER, Map.of(
                1, list(CARTOGRAPHY_SUPPLIES, MAPS),
                2, list(CARTOGRAPHY_SUPPLIES, MAPS),
                3, list(CARTOGRAPHY_SUPPLIES, MAPS),
                4, repeat(DECOR, 17),
                5, list(DECOR)
        ));
        register(result, VillagerTrades.TRADES, VillagerProfession.CLERIC, Map.of(
                1, repeat(ALCHEMY, 2), 2, repeat(ALCHEMY, 2), 3, repeat(ALCHEMY, 2),
                4, repeat(ALCHEMY, 3), 5, repeat(ALCHEMY, 2)
        ));
        register(result, VillagerTrades.TRADES, VillagerProfession.ARMORER, Map.of(
                1, sequence(SMITHING_MATERIALS, 1, ARMOR, 4),
                2, list(SMITHING_MATERIALS, GENERAL, ARMOR, ARMOR),
                3, sequence(SMITHING_MATERIALS, 2, ARMOR, 3),
                4, repeat(ARMOR, 2), 5, repeat(ARMOR, 2)
        ));
        register(result, VillagerTrades.TRADES, VillagerProfession.WEAPONSMITH, Map.of(
                1, sequence(SMITHING_MATERIALS, 1, WEAPONS, 2),
                2, list(SMITHING_MATERIALS, GENERAL),
                3, list(SMITHING_MATERIALS),
                4, list(SMITHING_MATERIALS, WEAPONS),
                5, list(WEAPONS)
        ));
        register(result, VillagerTrades.TRADES, VillagerProfession.TOOLSMITH, Map.of(
                1, sequence(SMITHING_MATERIALS, 1, TOOLS, 4),
                2, list(SMITHING_MATERIALS, GENERAL),
                3, sequence(SMITHING_MATERIALS, 1, TOOLS, 4),
                4, list(SMITHING_MATERIALS, TOOLS, TOOLS),
                5, list(TOOLS)
        ));
        register(result, VillagerTrades.TRADES, VillagerProfession.BUTCHER, Map.of(
                1, sequence(RAW_MEAT, 3, COOKED_FOOD, 1),
                2, sequence(GENERAL, 1, COOKED_FOOD, 2),
                3, repeat(RAW_MEAT, 2), 4, list(GENERAL), 5, list(GENERAL)
        ));
        register(result, VillagerTrades.TRADES, VillagerProfession.LEATHERWORKER, Map.of(
                1, sequence(LEATHER_MATERIALS, 1, LEATHER_GOODS, 2),
                2, sequence(GENERAL, 1, LEATHER_GOODS, 2),
                3, list(LEATHER_MATERIALS, LEATHER_GOODS),
                4, list(GENERAL, LEATHER_GOODS),
                5, repeat(LEATHER_GOODS, 2)
        ));
        register(result, VillagerTrades.TRADES, VillagerProfession.MASON, Map.of(
                1, repeat(STONEWORK, 2), 2, repeat(STONEWORK, 2), 3, repeat(STONEWORK, 7),
                4, sequence(QUARTZ, 1, TERRACOTTA, 32), 5, repeat(QUARTZ, 2)
        ));
    }

    private static void register(
            Map<PoolKey, IdentityHashMap<VillagerTrades.ItemListing, TradeCategoryId>> result,
            Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> pools,
            VillagerProfession profession,
            Map<Integer, List<TradeCategoryId>> layout
    ) {
        Int2ObjectMap<VillagerTrades.ItemListing[]> professionPools = pools.get(profession);
        if (professionPools == null) {
            return;
        }

        layout.forEach((level, categories) -> {
            VillagerTrades.ItemListing[] listings = professionPools.get(level.intValue());
            if (listings == null || listings.length != categories.size()) {
                return;
            }
            IdentityHashMap<VillagerTrades.ItemListing, TradeCategoryId> classifications =
                    result.computeIfAbsent(new PoolKey(profession, level), ignored -> new IdentityHashMap<>());
            for (int index = 0; index < listings.length; index++) {
                classifications.put(listings[index], categories.get(index));
            }
        });
    }

    private static TradeCategoryId category(String path) {
        return new TradeCategoryId(Villager_potential.MODID, path);
    }

    private static List<TradeCategoryId> list(TradeCategoryId... categories) {
        return List.of(categories);
    }

    private static List<TradeCategoryId> repeat(TradeCategoryId category, int count) {
        return sequence(category, count);
    }

    private static List<TradeCategoryId> sequence(Object... categoryCounts) {
        ArrayList<TradeCategoryId> result = new ArrayList<>();
        for (int index = 0; index < categoryCounts.length; index += 2) {
            TradeCategoryId category = (TradeCategoryId) categoryCounts[index];
            int count = (Integer) categoryCounts[index + 1];
            for (int occurrence = 0; occurrence < count; occurrence++) {
                result.add(category);
            }
        }
        return List.copyOf(result);
    }

    private record PoolKey(VillagerProfession profession, int level) {
    }
}
