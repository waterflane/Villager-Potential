package org.waterflane.villager_potential;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.waterflane.villager_potential.core.SpecializationBiasConfig;

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

    static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean logDirtBlock;
    public static int magicNumber;
    public static String magicNumberIntroduction;
    public static Set<Item> items;

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
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

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        logDirtBlock = LOG_DIRT_BLOCK.get();
        magicNumber = MAGIC_NUMBER.get();
        magicNumberIntroduction = MAGIC_NUMBER_INTRODUCTION.get();

        // convert the list of strings into a set of items
        items = ITEM_STRINGS.get().stream().map(itemName -> BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemName))).collect(Collectors.toSet());
    }
}
