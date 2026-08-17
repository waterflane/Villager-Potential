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

    private static final ModConfigSpec.DoubleValue SPECIALIZATION_MINIMUM_BIAS = BUILDER
            .comment("Fraction of specialization weights expressed at minimum professional skill (0 = neutral, 1 = full).")
            .defineInRange("specializationBias.minimumStrength", 0.1, 0.0, 1.0);
    private static final ModConfigSpec.DoubleValue SPECIALIZATION_MAXIMUM_BIAS = BUILDER
            .comment("Maximum fraction of specialization weights expressed by experienced villagers.")
            .defineInRange("specializationBias.maximumStrength", 1.0, 0.0, 1.0);
    private static final ModConfigSpec.DoubleValue SPECIALIZATION_BIAS_CURVE_EXPONENT = BUILDER
            .comment("Exponent for skill-to-specialization bias growth; values above 1 delay strong bias.")
            .defineInRange("specializationBias.curveExponent", 2.0, 0.01, 100.0);
    private static final ModConfigSpec.IntValue TRADE_HISTORY_MAXIMUM_ENTRIES = BUILDER
            .comment("Maximum logical offer history entries retained per villager profession.")
            .defineInRange("tradeHistory.maximumEntriesPerProfession", 128, 1, 4096);
    private static final ModConfigSpec.DoubleValue SEEN_TRADE_WEIGHT_MULTIPLIER = BUILDER
            .comment("Weight multiplier for an offer this villager has previously seen (1 = no penalty, 0 = blacklist).")
            .defineInRange("tradeMemory.seenTradeWeightMultiplier", 0.25, 0.0, 1.0);
    private static final ModConfigSpec.LongValue WEIGHTED_MEMORY_RECOVERY_TIME = BUILDER
            .comment("Profession ticks until a WEIGHTED_MEMORY penalty fully recovers.")
            .defineInRange("tradeMemory.weightedPenaltyRecoveryTime", 24_000L, 1L, Long.MAX_VALUE);
    private static final ModConfigSpec.DoubleValue MINIMUM_CANDIDATE_WEIGHT = BUILDER
            .comment("Absolute floor for a memory-penalized candidate, capped at its unpenalized weight.")
            .defineInRange("tradeMemory.minimumCandidateWeight", 0.01, 0.0, Double.MAX_VALUE);
    private static final ModConfigSpec.LongValue EXHAUST_RECOVERY_TIME = BUILDER
            .comment("Profession ticks after which an EXHAUST candidate becomes eligible again.")
            .defineInRange("tradeMemory.exhaustRecoveryTime", 24_000L, 1L, Long.MAX_VALUE);
    private static final ModConfigSpec.LongValue CYCLIC_RESET_TIME = BUILDER
            .comment("Profession ticks all CYCLIC candidates must be idle before a fresh cycle begins.")
            .defineInRange("tradeMemory.cyclicResetTime", 24_000L, 1L, Long.MAX_VALUE);
    private static final ModConfigSpec.LongValue RARE_TRADE_RECOVERY_TIME = BUILDER
            .comment("Shorter recovery for configured rare results (0 disables rare-trade protection).")
            .defineInRange("tradeMemory.rareTradeRecoveryTime", 0L, 0L, Long.MAX_VALUE);
    private static final ModConfigSpec.ConfigValue<List<? extends String>> RARE_TRADE_RESULTS =
            BUILDER.comment("Result item IDs protected by rareTradeRecoveryTime.")
                    .defineListAllowEmpty(
                            "tradeMemory.rareTradeResultItems",
                            List.of(),
                            Config::validateResourceLocation
                    );
    private static final ModConfigSpec.EnumValue<TradePaletteRerollStrategy>
            TRADE_PALETTE_REROLL_STRATEGY = BUILDER
            .comment("Trade palette reroll strategy.")
            .defineEnum(
                    "tradePalette.rerollStrategy",
                    TradePaletteRerollStrategy.PERSISTENT
            );
    private static final ModConfigSpec.DoubleValue MARKET_DEMAND_MINIMUM = BUILDER
            .comment("Minimum demand score for one logical trade.")
            .defineInRange("marketDemand.minimum", 0.0, -Double.MAX_VALUE, Double.MAX_VALUE);
    private static final ModConfigSpec.DoubleValue MARKET_DEMAND_BASELINE = BUILDER
            .comment("Demand score approached while a logical trade remains unused.")
            .defineInRange("marketDemand.baseline", 0.0, -Double.MAX_VALUE, Double.MAX_VALUE);
    private static final ModConfigSpec.DoubleValue MARKET_DEMAND_MAXIMUM = BUILDER
            .comment("Maximum demand score for one logical trade.")
            .defineInRange("marketDemand.maximum", 100.0, -Double.MAX_VALUE, Double.MAX_VALUE);
    private static final ModConfigSpec.DoubleValue MARKET_DEMAND_INCREASE_PER_PURCHASE = BUILDER
            .comment("Demand added by one completed purchase.")
            .defineInRange("marketDemand.increasePerPurchase", 1.0, Double.MIN_NORMAL, Double.MAX_VALUE);
    private static final ModConfigSpec.DoubleValue MARKET_DEMAND_DECAY_PER_TICK = BUILDER
            .comment("Demand moved toward baseline per elapsed server tick (0 disables decay).")
            .defineInRange("marketDemand.decayPerTick", 1.0 / 1_200.0, 0.0, Double.MAX_VALUE);
    private static final ModConfigSpec.DoubleValue MARKET_DEMAND_MINIMUM_PRICE_MULTIPLIER = BUILDER
            .comment("Price multiplier at minimum demand (1 keeps low demand neutral).")
            .defineInRange("marketDemand.minimumPriceMultiplier", 1.0, 0.01, 1.0);
    private static final ModConfigSpec.DoubleValue MARKET_DEMAND_MAXIMUM_PRICE_MULTIPLIER = BUILDER
            .comment("Price multiplier at maximum demand.")
            .defineInRange("marketDemand.maximumPriceMultiplier", 2.0, 1.0, 64.0);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean logDirtBlock;
    public static int magicNumber;
    public static String magicNumberIntroduction;
    public static Set<Item> items;

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }

    private static boolean validateResourceLocation(final Object obj) {
        return obj instanceof String value && ResourceLocation.tryParse(value) != null;
    }

    static SpecializationBiasConfig specializationBiasConfig() {
        double maximumBias = SPECIALIZATION_MAXIMUM_BIAS.get();
        double minimumBias = Math.min(SPECIALIZATION_MINIMUM_BIAS.get(), maximumBias);
        return new SpecializationBiasConfig(
                VillagerPotentialAttachments.SKILL_PROGRESSION_CONFIG.minimumSkill(),
                VillagerPotentialAttachments.SKILL_PROGRESSION_CONFIG.maximumSkill(),
                minimumBias,
                maximumBias,
                SPECIALIZATION_BIAS_CURVE_EXPONENT.get()
        );
    }

    public static int tradeHistoryMaximumEntries() {
        return TRADE_HISTORY_MAXIMUM_ENTRIES.get();
    }

    public static double seenTradeWeightMultiplier() {
        return SEEN_TRADE_WEIGHT_MULTIPLIER.get();
    }

    public static TradeMemoryRecoveryConfig tradeMemoryRecoveryConfig() {
        return new TradeMemoryRecoveryConfig(
                WEIGHTED_MEMORY_RECOVERY_TIME.get(),
                MINIMUM_CANDIDATE_WEIGHT.get(),
                EXHAUST_RECOVERY_TIME.get(),
                CYCLIC_RESET_TIME.get(),
                RARE_TRADE_RECOVERY_TIME.get()
        );
    }

    public static boolean isRareTradeProtected(TradeKey candidate) {
        return candidate instanceof TradeKey.Offer offer
                && RARE_TRADE_RESULTS.get().contains(offer.result().itemId());
    }

    public static TradePaletteRerollStrategy tradePaletteRerollStrategy() {
        return TRADE_PALETTE_REROLL_STRATEGY.get();
    }

    public static MarketDemandConfig marketDemandConfig() {
        double minimum = MARKET_DEMAND_MINIMUM.get();
        double maximum = Math.max(MARKET_DEMAND_MAXIMUM.get(), minimum);
        double baseline = Math.max(
                minimum,
                Math.min(maximum, MARKET_DEMAND_BASELINE.get())
        );
        return new MarketDemandConfig(
                minimum,
                baseline,
                maximum,
                MARKET_DEMAND_INCREASE_PER_PURCHASE.get(),
                MARKET_DEMAND_DECAY_PER_TICK.get()
        );
    }

    public static MarketDemandPriceConfig marketDemandPriceConfig() {
        return new MarketDemandPriceConfig(
                MARKET_DEMAND_MINIMUM_PRICE_MULTIPLIER.get(),
                MARKET_DEMAND_MAXIMUM_PRICE_MULTIPLIER.get()
        );
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
