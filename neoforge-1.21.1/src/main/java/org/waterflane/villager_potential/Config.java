package org.waterflane.villager_potential;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.waterflane.villager_potential.core.MarketDemandConfig;
import org.waterflane.villager_potential.core.MarketDemandPriceConfig;
import org.waterflane.villager_potential.core.SpecializationBiasConfig;
import org.waterflane.villager_potential.core.TradeKey;
import org.waterflane.villager_potential.core.TradeMemoryRecoveryConfig;
import org.waterflane.villager_potential.core.TradePaletteRerollStrategy;
import org.waterflane.villager_potential.core.ProfessionId;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
@EventBusSubscriber(modid = Villager_potential.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER.comment("Whether to log the dirt block on common setup").define("logDirtBlock", true);

    private static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER.comment("A magic number").defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER.comment("What you want the introduction message to be for the magic number").define("magicNumberIntroduction", "The magic number is... ");

    // a list of strings that are treated as resource locations for items
    private static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER.comment("A list of items to log on common setup.").defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), Config::validateItemName);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean logDirtBlock;
    public static int magicNumber;
    public static String magicNumberIntroduction;
    public static Set<Item> items;

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }

    static SpecializationBiasConfig specializationBiasConfig(ProfessionId profession) {
        var skillConfig = ServerConfig.gameplayConfig().skill();
        return ServerConfig.tradeConfig().specializations().biasFor(
                profession,
                skillConfig.minimumSkill(),
                skillConfig.maximumSkill()
        );
    }

    public static int tradeHistoryMaximumEntries() {
        return ServerConfig.tradeConfig().palette().maximumHistoryEntries();
    }

    public static double seenTradeWeightMultiplier() {
        return ServerConfig.tradeConfig().palette().seenTradeWeightMultiplier();
    }

    public static TradeMemoryRecoveryConfig tradeMemoryRecoveryConfig() {
        return ServerConfig.tradeConfig().palette().recovery();
    }

    public static boolean isRareTradeProtected(TradeKey candidate) {
        return ServerConfig.tradeConfig().palette().isRareProtected(candidate);
    }

    public static TradePaletteRerollStrategy tradePaletteRerollStrategy() {
        return ServerConfig.tradeConfig().palette().mode();
    }

    public static MarketDemandConfig marketDemandConfig() {
        return ServerConfig.tradeConfig().economy().demand();
    }

    public static MarketDemandPriceConfig marketDemandPriceConfig() {
        return ServerConfig.tradeConfig().economy().price();
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        logDirtBlock = LOG_DIRT_BLOCK.get();
        magicNumber = MAGIC_NUMBER.get();
        magicNumberIntroduction = MAGIC_NUMBER_INTRODUCTION.get();

        // convert the list of strings into a set of items
        items = ITEM_STRINGS.get().stream().map(itemName -> BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemName))).collect(Collectors.toSet());
    }
}
