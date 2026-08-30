package org.waterflane.villager_potential;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.common.ForgeConfigSpec;
import org.waterflane.villager_potential.core.AptitudeGenerationConfig;
import org.waterflane.villager_potential.core.AptitudeInheritanceConfig;
import org.waterflane.villager_potential.core.CareerProgressionConfig;
import org.waterflane.villager_potential.core.MarketDemandConfig;
import org.waterflane.villager_potential.core.MarketDemandPriceConfig;
import org.waterflane.villager_potential.core.MarketDemandStockConfig;
import org.waterflane.villager_potential.core.MarketEconomyConfig;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.ProfessionActivityConfig;
import org.waterflane.villager_potential.core.ProfessionLevelThresholds;
import org.waterflane.villager_potential.core.RareTalentConfig;
import org.waterflane.villager_potential.core.SkillProgressionConfig;
import org.waterflane.villager_potential.core.SpecializationConfig;
import org.waterflane.villager_potential.core.TradeMemoryRecoveryConfig;
import org.waterflane.villager_potential.core.TradePaletteConfig;
import org.waterflane.villager_potential.core.TradePaletteRerollStrategy;
import org.waterflane.villager_potential.core.VillagerPotentialConfig;
import org.waterflane.villager_potential.core.VillagerPotentialConfiguration;
import org.waterflane.villager_potential.core.VillagerTradeConfig;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** World-owned SERVER settings mapped into loader-neutral core configuration. */
public final class ServerConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    static final VillagerPotentialConfig DEFAULT_GAMEPLAY = VillagerPotentialConfig.DEFAULT;
    static final VillagerTradeConfig DEFAULT_TRADES = VillagerTradeConfig.DEFAULT;
    static final MarketDemandStockConfig DEFAULT_MARKET_DEMAND_STOCK =
            DEFAULT_TRADES.economy().stock();

    private static final ForgeConfigSpec.BooleanValue APTITUDE_ENABLED;
    private static final ForgeConfigSpec.DoubleValue APTITUDE_MEAN;
    private static final ForgeConfigSpec.DoubleValue APTITUDE_VARIANCE;
    private static final ForgeConfigSpec.DoubleValue APTITUDE_MINIMUM;
    private static final ForgeConfigSpec.DoubleValue APTITUDE_MAXIMUM;
    private static final ForgeConfigSpec.BooleanValue RARE_TALENTS_ENABLED;
    private static final ForgeConfigSpec.DoubleValue RARE_TALENT_CHANCE;
    private static final ForgeConfigSpec.DoubleValue RARE_TALENT_STRENGTH;
    private static final ForgeConfigSpec.BooleanValue INHERITANCE_ENABLED;
    private static final ForgeConfigSpec.DoubleValue INHERITANCE_STRENGTH;
    private static final ForgeConfigSpec.DoubleValue RANDOM_CONTRIBUTION;
    private static final ForgeConfigSpec.DoubleValue MUTATION_CHANCE;
    private static final ForgeConfigSpec.DoubleValue MUTATION_VARIANCE;
    private static final ForgeConfigSpec.BooleanValue CAREER_ENABLED;
    private static final ForgeConfigSpec.BooleanValue CAREER_ADULTS_ONLY;
    private static final ForgeConfigSpec.BooleanValue CAREER_REQUIRE_WORK_ACTIVITY;
    private static final ForgeConfigSpec.BooleanValue SKILL_ENABLED;
    private static final ForgeConfigSpec.DoubleValue SKILL_BASE_RATE;
    private static final ForgeConfigSpec.DoubleValue SKILL_APTITUDE_INFLUENCE;
    private static final ForgeConfigSpec.DoubleValue SKILL_MINIMUM;
    private static final ForgeConfigSpec.DoubleValue SKILL_MAXIMUM;
    private static final ForgeConfigSpec.BooleanValue ACTIVITY_ENABLED;
    private static final ForgeConfigSpec.DoubleValue ACTIVITY_GAIN_PER_TRADE;
    private static final ForgeConfigSpec.DoubleValue ACTIVITY_DECAY_RATE;
    private static final ForgeConfigSpec.DoubleValue ACTIVITY_BASELINE;
    private static final ForgeConfigSpec.DoubleValue ACTIVITY_MAXIMUM;
    private static final ForgeConfigSpec.DoubleValue LEVEL_NOVICE;
    private static final ForgeConfigSpec.DoubleValue LEVEL_APPRENTICE;
    private static final ForgeConfigSpec.DoubleValue LEVEL_JOURNEYMAN;
    private static final ForgeConfigSpec.DoubleValue LEVEL_EXPERT;
    private static final ForgeConfigSpec.DoubleValue LEVEL_MASTER;
    private static final ForgeConfigSpec.BooleanValue SPECIALIZATIONS_ENABLED;
    private static final ForgeConfigSpec.DoubleValue SPECIALIZATION_GLOBAL_STRENGTH;
    private static final ForgeConfigSpec.DoubleValue SPECIALIZATION_MINIMUM_BIAS;
    private static final ForgeConfigSpec.DoubleValue SPECIALIZATION_MAXIMUM_BIAS;
    private static final ForgeConfigSpec.DoubleValue SPECIALIZATION_CURVE_EXPONENT;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>>
            SPECIALIZATION_PROFESSION_OVERRIDES;
    private static final ForgeConfigSpec.EnumValue<TradePaletteRerollStrategy> PALETTE_MODE;
    private static final ForgeConfigSpec.DoubleValue REPEATED_TRADE_PENALTY;
    private static final ForgeConfigSpec.DoubleValue MINIMUM_CANDIDATE_WEIGHT;
    private static final ForgeConfigSpec.LongValue MEMORY_DECAY_TICKS;
    private static final ForgeConfigSpec.BooleanValue RARE_TRADE_PROTECTION_ENABLED;
    private static final ForgeConfigSpec.LongValue RARE_TRADE_RECOVERY_TICKS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> RARE_TRADE_RESULTS;
    private static final ForgeConfigSpec.LongValue EXHAUSTION_RECOVERY_TICKS;
    private static final ForgeConfigSpec.LongValue CYCLE_RECOVERY_TICKS;
    private static final ForgeConfigSpec.BooleanValue DEMAND_ENABLED;
    private static final ForgeConfigSpec.DoubleValue DEMAND_GAIN_PER_USE;
    private static final ForgeConfigSpec.DoubleValue DEMAND_DECAY_PER_TICK;
    private static final ForgeConfigSpec.DoubleValue DEMAND_MINIMUM;
    private static final ForgeConfigSpec.DoubleValue DEMAND_BASELINE;
    private static final ForgeConfigSpec.DoubleValue DEMAND_MAXIMUM;
    private static final ForgeConfigSpec.BooleanValue PRICE_INFLUENCE_ENABLED;
    private static final ForgeConfigSpec.DoubleValue MINIMUM_PRICE_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue MAXIMUM_PRICE_MULTIPLIER;
    private static final ForgeConfigSpec.BooleanValue DEMAND_INFLUENCES_STOCK;
    private static final ForgeConfigSpec.DoubleValue STOCK_INFLUENCE_STRENGTH;
    private static final ForgeConfigSpec.IntValue MAXIMUM_ADDITIONAL_USES;
    private static final ForgeConfigSpec.IntValue MAXIMUM_USES_PER_OFFER;
    private static final ForgeConfigSpec.BooleanValue DIAGNOSTIC_LOGGING;
    private static final ForgeConfigSpec.BooleanValue DETAILED_WEIGHT_LOGGING;

    static {
        AptitudeGenerationConfig aptitude = DEFAULT_GAMEPLAY.aptitude();
        BUILDER.push("aptitude");
        APTITUDE_ENABLED = BUILDER
                .comment("Generate immutable profession aptitudes for new villagers; false gives new villagers neutral 1.0 values without rerolling existing ones.")
                .define("enabled", aptitude.enabled());
        APTITUDE_MEAN = BUILDER
                .comment("Center of the aptitude distribution from 0.0 to 64.0; 1.0 is neutral progression speed.")
                .defineInRange("mean", aptitude.mean(), 0.0, 64.0);
        APTITUDE_VARIANCE = BUILDER
                .comment("Distribution variance from 0.0 to 64.0 aptitude squared; standard deviation is its square root.")
                .defineInRange("variance", aptitude.variance(), 0.0, 64.0);
        APTITUDE_MINIMUM = BUILDER
                .comment("Lowest generated aptitude multiplier from 0.0 to 64.0 progression-speed units.")
                .defineInRange("minimum", aptitude.minimum(), 0.0, 64.0);
        APTITUDE_MAXIMUM = BUILDER
                .comment("Highest generated aptitude multiplier from 0.0 to 64.0, including rare talents.")
                .defineInRange("maximum", aptitude.maximum(), 0.0, 64.0);
        BUILDER.pop();

        RareTalentConfig rareTalents = aptitude.rareTalents();
        BUILDER.push("rareTalents");
        RARE_TALENTS_ENABLED = BUILDER
                .comment("Allow new aptitudes to use the exceptional upper-tail distribution; false leaves ordinary generation unchanged.")
                .define("enabled", rareTalents.enabled());
        RARE_TALENT_CHANCE = BUILDER
                .comment("Chance from 0.0 to 1.0 that one generated profession aptitude is a rare talent.")
                .defineInRange("chance", rareTalents.chance(), 0.0, 1.0);
        RARE_TALENT_STRENGTH = BUILDER
                .comment("Minimum rare-talent offset from 0.0 to 10.0 aptitude standard deviations.")
                .defineInRange("strength", rareTalents.strength(), 0.0, 10.0);
        BUILDER.pop();

        AptitudeInheritanceConfig inheritance = DEFAULT_GAMEPLAY.inheritance();
        BUILDER.push("inheritance");
        INHERITANCE_ENABLED = BUILDER
                .comment("Let newly bred villagers derive aptitudes from parents; false gives children freshly generated values.")
                .define("enabled", inheritance.enabled());
        INHERITANCE_STRENGTH = BUILDER
                .comment("Parent-average weight from 0.0 to 1.0 in each child aptitude.")
                .defineInRange("inheritanceStrength", inheritance.inheritanceStrength(), 0.0, 1.0);
        RANDOM_CONTRIBUTION = BUILDER
                .comment("Fresh-generation weight from 0.0 to 1.0; together with inheritanceStrength it cannot exceed 1.0.")
                .defineInRange("randomContribution", inheritance.randomContribution(), 0.0, 1.0);
        MUTATION_CHANCE = BUILDER
                .comment("Chance from 0.0 to 1.0 that an inherited aptitude receives a mutation.")
                .defineInRange("mutationChance", inheritance.mutationChance(), 0.0, 1.0);
        MUTATION_VARIANCE = BUILDER
                .comment("Mutation variance from 0.0 to 64.0 aptitude squared; standard deviation is its square root.")
                .defineInRange("mutationVariance", inheritance.mutationVariance(), 0.0, 64.0);
        BUILDER.pop();

        CareerProgressionConfig career = DEFAULT_GAMEPLAY.career();
        BUILDER.push("career");
        CAREER_ENABLED = BUILDER
                .comment("Count eligible loaded server ticks as tenure; false preserves history. Valid non-nitwit employment is always required.")
                .define("enabled", career.enabled());
        CAREER_ADULTS_ONLY = BUILDER
                .comment("Require adulthood for a loaded profession tick to count toward tenure.")
                .define("adultsOnly", career.adultsOnly());
        CAREER_REQUIRE_WORK_ACTIVITY = BUILDER
                .comment("Require the villager's current brain activity to be WORK for a tick to count.")
                .define("requireWorkActivity", career.requireWorkActivity());
        BUILDER.pop();

        SkillProgressionConfig skill = DEFAULT_GAMEPLAY.skill();
        BUILDER.push("skill");
        SKILL_ENABLED = BUILDER
                .comment("Convert eligible profession tenure into persistent learned skill; false preserves skill already earned.")
                .define("enabled", skill.enabled());
        SKILL_BASE_RATE = BUILDER
                .comment("Skill gained per eligible server tick from 0.0 to 1.0 at neutral aptitude and activity.")
                .defineInRange("baseProgressionRate", skill.progressionRate(), 0.0, 1.0);
        SKILL_APTITUDE_INFLUENCE = BUILDER
                .comment("Fraction from 0.0 to 1.0 of aptitude's effect on skill gain; 0.0 is neutral, 1.0 is full.")
                .defineInRange("aptitudeInfluence", skill.aptitudeInfluence(), 0.0, 1.0);
        SKILL_MINIMUM = BUILDER
                .comment("Lower skill bound from 0.0 to 1000000.0; existing stored history is never reset on reload.")
                .defineInRange("minimum", skill.minimumSkill(), 0.0, 1_000_000.0);
        SKILL_MAXIMUM = BUILDER
                .comment("Skill gain cap from 0.0 to 1000000.0; lowering it never reduces already-earned skill.")
                .defineInRange("maximum", skill.maximumSkill(), 0.0, 1_000_000.0);
        BUILDER.pop();

        ProfessionActivityConfig activity = DEFAULT_GAMEPLAY.activity();
        BUILDER.push("activity");
        ACTIVITY_ENABLED = BUILDER
                .comment("Let successful trades accelerate progression; false uses a neutral 1.0 multiplier and preserves old activity history.")
                .define("enabled", activity.enabled());
        ACTIVITY_GAIN_PER_TRADE = BUILDER
                .comment("Activity multiplier added per successful trade from 0.0 to 64.0; 0.0 adds no acceleration.")
                .defineInRange("gainPerSuccessfulTrade", activity.increasePerTrade(), 0.0, 64.0);
        ACTIVITY_DECAY_RATE = BUILDER
                .comment("Multiplier decay per server tick from 0.0 to 64.0 toward baseline; 0.0 disables decay.")
                .defineInRange("decayRate", activity.decayPerTick(), 0.0, 64.0);
        ACTIVITY_BASELINE = BUILDER
                .comment("Baseline multiplier from 0.5 to 64.0; 1.0 leaves the base progression rate unchanged.")
                .defineInRange("baseline", activity.baseline(), activity.minimum(), 64.0);
        ACTIVITY_MAXIMUM = BUILDER
                .comment("Maximum trade-activity multiplier from 0.01 to 64.0; it must be at least baseline.")
                .defineInRange("maximumMultiplier", activity.maximum(), 0.01, 64.0);
        BUILDER.pop();

        ProfessionLevelThresholds levels = skill.professionLevelThresholds();
        BUILDER.push("levels");
        LEVEL_NOVICE = threshold("novice", levels.noviceSkill(), "Novice");
        LEVEL_APPRENTICE = threshold("apprentice", levels.apprenticeSkill(), "Apprentice");
        LEVEL_JOURNEYMAN = threshold("journeyman", levels.journeymanSkill(), "Journeyman");
        LEVEL_EXPERT = threshold("expert", levels.expertSkill(), "Expert");
        LEVEL_MASTER = threshold("master", levels.masterSkill(), "Master");
        BUILDER.pop();

        SpecializationConfig specializations = DEFAULT_TRADES.specializations();
        BUILDER.push("specializations");
        SPECIALIZATIONS_ENABLED = BUILDER
                .comment("Enable datapack-defined professional specialization weights; false keeps stored assignments but uses neutral weights.")
                .define("enabled", specializations.enabled());
        SPECIALIZATION_GLOBAL_STRENGTH = BUILDER
                .comment("Global specialization strength from 0.0 (neutral) to 1.0 (full configured bias).")
                .defineInRange("globalStrength", specializations.globalStrength(), 0.0, 1.0);
        SPECIALIZATION_MINIMUM_BIAS = BUILDER
                .comment("Bias fraction from 0.0 to 1.0 expressed at minimum professional skill.")
                .defineInRange("minimumBias", specializations.minimumBiasStrength(), 0.0, 1.0);
        SPECIALIZATION_MAXIMUM_BIAS = BUILDER
                .comment("Bias fraction from 0.0 to 1.0 expressed at maximum professional skill; must be at least minimumBias.")
                .defineInRange("maximumBias", specializations.maximumBiasStrength(), 0.0, 1.0);
        SPECIALIZATION_CURVE_EXPONENT = BUILDER
                .comment("Positive 0.01-100.0 exponent for skill-strengthened bias; larger values delay strong specialization.")
                .defineInRange("curveExponent", specializations.curveExponent(), 0.01, 100.0);
        SPECIALIZATION_PROFESSION_OVERRIDES = BUILDER
                .comment(
                        "Optional per-profession strength overrides as namespaced_id=value, for example minecraft:librarian=0.75.",
                        "Values range from 0.0 to 1.0. These do not replace datapack specialization definitions."
                )
                .defineListAllowEmpty("professionStrengthOverrides", List.of(), String.class::isInstance);
        BUILDER.pop();

        TradePaletteConfig palette = DEFAULT_TRADES.palette();
        BUILDER.push("palette");
        PALETTE_MODE = BUILDER
                .comment(
                        "Trade palette policy: PERSISTENT, VANILLA, WEIGHTED_MEMORY, EXHAUST, or CYCLIC.",
                        "Mode changes never delete stored learned trades or history."
                )
                .defineEnum("mode", palette.mode());
        BUILDER.pop();

        BUILDER.push("memory");
        REPEATED_TRADE_PENALTY = BUILDER
                .comment("Repeated-trade weight penalty from 0.0 (none) to 1.0 (full), used only by WEIGHTED_MEMORY.")
                .defineInRange("repeatedTradePenalty", palette.repeatedTradePenalty(), 0.0, 1.0);
        MINIMUM_CANDIDATE_WEIGHT = BUILDER
                .comment("Absolute candidate-weight floor from 0.0 upward while a repeated-trade penalty recovers.")
                .defineInRange("minimumCandidateWeight", palette.recovery().minimumCandidateWeight(), 0.0, Double.MAX_VALUE);
        MEMORY_DECAY_TICKS = BUILDER
                .comment("Eligible profession ticks from 1 upward until a WEIGHTED_MEMORY penalty fully decays.")
                .defineInRange("decayTicks", palette.recovery().weightedPenaltyRecoveryTime(), 1L, Long.MAX_VALUE);
        RARE_TRADE_PROTECTION_ENABLED = BUILDER
                .comment("Give configured rare result items the shorter recovery below; false leaves their history unchanged and unprotected.")
                .define("rareTradeProtectionEnabled", palette.rareTradeProtectionEnabled());
        RARE_TRADE_RECOVERY_TICKS = BUILDER
                .comment("Rare-result recovery in eligible profession ticks; 0 disables shortened recovery.")
                .defineInRange("rareTradeRecoveryTicks", palette.recovery().rareTradeRecoveryTime(), 0L, Long.MAX_VALUE);
        RARE_TRADE_RESULTS = BUILDER
                .comment("Namespaced result item IDs eligible for rare-trade recovery protection.")
                .defineListAllowEmpty("rareTradeResultItems", List.of(), ServerConfig::validNamespacedId);
        EXHAUSTION_RECOVERY_TICKS = BUILDER
                .comment("Eligible profession ticks from 1 upward before an EXHAUST candidate can return.")
                .defineInRange("exhaustionRecoveryTicks", palette.recovery().exhaustRecoveryTime(), 1L, Long.MAX_VALUE);
        CYCLE_RECOVERY_TICKS = BUILDER
                .comment("Eligible profession ticks from 1 upward that all CYCLIC candidates must be idle before reset.")
                .defineInRange("cycleRecoveryTicks", palette.recovery().cyclicResetTime(), 1L, Long.MAX_VALUE);
        BUILDER.pop();

        MarketEconomyConfig economy = DEFAULT_TRADES.economy();
        MarketDemandConfig demand = economy.demand();
        BUILDER.push("economy").push("demand");
        DEMAND_ENABLED = BUILDER
                .comment("Record per-trade demand from successful uses; false preserves stored demand without updating or applying it.")
                .define("enabled", demand.enabled());
        DEMAND_GAIN_PER_USE = BUILDER
                .comment("Demand score gained per successful trade use; positive score units per use.")
                .defineInRange("gainPerSuccessfulUse", demand.increasePerPurchase(), Double.MIN_NORMAL, Double.MAX_VALUE);
        DEMAND_DECAY_PER_TICK = BUILDER
                .comment("Demand score moved toward baseline per server tick; 0.0 disables decay.")
                .defineInRange("decayPerTick", demand.decayPerTick(), 0.0, Double.MAX_VALUE);
        DEMAND_MINIMUM = BUILDER
                .comment("Finite lower bound for each logical trade's demand score.")
                .defineInRange("minimum", demand.minimum(), -Double.MAX_VALUE, Double.MAX_VALUE);
        DEMAND_BASELINE = BUILDER
                .comment("Finite neutral demand score approached during decay; must lie within minimum and maximum.")
                .defineInRange("baseline", demand.baseline(), -Double.MAX_VALUE, Double.MAX_VALUE);
        DEMAND_MAXIMUM = BUILDER
                .comment("Finite upper bound for each logical trade's demand score.")
                .defineInRange("maximum", demand.maximum(), -Double.MAX_VALUE, Double.MAX_VALUE);
        BUILDER.pop();

        MarketDemandPriceConfig price = economy.price();
        BUILDER.push("price");
        PRICE_INFLUENCE_ENABLED = BUILDER
                .comment("Apply demand to prices independently of stock influence; false preserves vanilla-adjusted prices.")
                .define("enabled", price.enabled());
        MINIMUM_PRICE_MULTIPLIER = BUILDER
                .comment("Price multiplier from 0.01 to 1.0 at minimum demand; baseline remains neutral at 1.0.")
                .defineInRange("minimumMultiplier", price.minimumMultiplier(), 0.01, 1.0);
        MAXIMUM_PRICE_MULTIPLIER = BUILDER
                .comment("Price multiplier from 1.0 to 64.0 at maximum demand; item stack limits still apply.")
                .defineInRange("maximumMultiplier", price.maximumMultiplier(), 1.0, 64.0);
        BUILDER.pop();

        MarketDemandStockConfig stock = economy.stock();
        BUILDER.push("stock");
        DEMAND_INFLUENCES_STOCK = BUILDER
                .comment(
                        "Apply demand to stock independently of price influence after a vanilla-approved restock.",
                        "This never creates extra restocks or bypasses workstation and daily timing checks."
                )
                .define("enabled", stock.enabled());
        STOCK_INFLUENCE_STRENGTH = BUILDER
                .comment("Stock influence from 0.0 (neutral) to 1.0 (full configured additional-use cap).")
                .defineInRange("influenceStrength", stock.influenceStrength(), 0.0, 1.0);
        MAXIMUM_ADDITIONAL_USES = BUILDER
                .comment("Hard cap from 0 to 64 on uses demand may add to one offer per restock.")
                .defineInRange("maximumAdditionalUses", stock.maximumAdditionalUses(), 0, 64);
        MAXIMUM_USES_PER_OFFER = BUILDER
                .comment(
                        "Hard total-use ceiling from 1 to 64 above which demand cannot raise an offer.",
                        "Existing vanilla or modded offers above this value are never reduced."
                )
                .defineInRange("maximumUsesPerOffer", stock.maximumUsesPerOffer(), 1, 64);
        BUILDER.pop(2);

        BUILDER.push("debug");
        DIAGNOSTIC_LOGGING = BUILDER
                .comment("Log concise semantic Villager Potential lifecycle, trade and demand diagnostics.")
                .define("enabled", false);
        DETAILED_WEIGHT_LOGGING = BUILDER
                .comment(
                        "Log each resolved trade candidate weight; requires debug.enabled and may be noisy during trade generation."
                )
                .define("detailedTradeWeights", false);
        BUILDER.pop();
    }

    static final ForgeConfigSpec SPEC = BUILDER.build();
    private static volatile VillagerPotentialConfiguration activeConfiguration =
            VillagerPotentialConfiguration.DEFAULT;

    private ServerConfig() {
    }

    public static VillagerPotentialConfig gameplayConfig() {
        return activeConfiguration.gameplay();
    }

    private static Values currentValues() {
        return migrateLegacyDefaults(new Values(
                new AptitudeValues(APTITUDE_ENABLED.get(), APTITUDE_MEAN.get(), APTITUDE_VARIANCE.get(), APTITUDE_MINIMUM.get(), APTITUDE_MAXIMUM.get()),
                new RareTalentValues(RARE_TALENTS_ENABLED.get(), RARE_TALENT_CHANCE.get(), RARE_TALENT_STRENGTH.get()),
                new InheritanceValues(INHERITANCE_ENABLED.get(), INHERITANCE_STRENGTH.get(), RANDOM_CONTRIBUTION.get(), MUTATION_CHANCE.get(), MUTATION_VARIANCE.get()),
                new CareerValues(CAREER_ENABLED.get(), CAREER_ADULTS_ONLY.get(), true, CAREER_REQUIRE_WORK_ACTIVITY.get()),
                new SkillValues(SKILL_ENABLED.get(), SKILL_BASE_RATE.get(), SKILL_APTITUDE_INFLUENCE.get(), SKILL_MINIMUM.get(), SKILL_MAXIMUM.get()),
                new ActivityValues(ACTIVITY_ENABLED.get(), ACTIVITY_GAIN_PER_TRADE.get(), ACTIVITY_DECAY_RATE.get(), ACTIVITY_BASELINE.get(), ACTIVITY_MAXIMUM.get()),
                new LevelValues(LEVEL_NOVICE.get(), LEVEL_APPRENTICE.get(), LEVEL_JOURNEYMAN.get(), LEVEL_EXPERT.get(), LEVEL_MASTER.get())
        ));
    }

    /** Keeps worlds generated with the previous default curve on the new timing model. */
    static Values migrateLegacyDefaults(Values values) {
        Objects.requireNonNull(values, "values");
        SkillValues skill = values.skill();
        ActivityValues activity = values.activity();
        LevelValues levels = values.levels();
        VillagerPotentialConfig defaults = DEFAULT_GAMEPLAY;

        boolean originalSkillCurve = Double.compare(skill.baseProgressionRate(), 0.00005) == 0
                && Double.compare(skill.minimum(), 0.0) == 0
                && Double.compare(skill.maximum(), 5.0) == 0
                && Double.compare(levels.novice(), 0.0) == 0
                && Double.compare(levels.apprentice(), 0.2) == 0
                && Double.compare(levels.journeyman(), 0.5) == 0
                && Double.compare(levels.expert(), 1.0) == 0
                && Double.compare(levels.master(), 5.0) == 0;
        boolean intermediateSkillCurve = Double.compare(skill.baseProgressionRate(), 0.001) == 0
                && Double.compare(skill.minimum(), 0.0) == 0
                && Double.compare(skill.maximum(), 1.0) == 0
                && Double.compare(levels.novice(), 0.0) == 0
                && Double.compare(levels.apprentice(), 0.2) == 0
                && Double.compare(levels.journeyman(), 0.5) == 0
                && Double.compare(levels.expert(), 0.8) == 0
                && Double.compare(levels.master(), 1.0) == 0;
        boolean legacySkillCurve = originalSkillCurve || intermediateSkillCurve;
        boolean legacyActivityDecay = Double.compare(activity.gainPerSuccessfulTrade(), 0.1) == 0
                && Double.compare(activity.decayRate(), 0.0001) == 0
                && Double.compare(activity.baseline(), 1.0) == 0
                && Double.compare(activity.maximumMultiplier(), 2.0) == 0;

        SkillProgressionConfig defaultSkill = defaults.skill();
        ProfessionActivityConfig defaultActivity = defaults.activity();
        SkillValues migratedSkill = legacySkillCurve
                ? new SkillValues(
                        skill.enabled(),
                        defaultSkill.progressionRate(),
                        skill.aptitudeInfluence(),
                        defaultSkill.minimumSkill(),
                        defaultSkill.maximumSkill()
                )
                : skill;
        LevelValues migratedLevels = legacySkillCurve
                ? new LevelValues(
                        defaultSkill.professionLevelThresholds().noviceSkill(),
                        defaultSkill.professionLevelThresholds().apprenticeSkill(),
                        defaultSkill.professionLevelThresholds().journeymanSkill(),
                        defaultSkill.professionLevelThresholds().expertSkill(),
                        defaultSkill.professionLevelThresholds().masterSkill()
                )
                : levels;
        ActivityValues migratedActivity = legacyActivityDecay
                ? new ActivityValues(
                        activity.enabled(),
                        activity.gainPerSuccessfulTrade(),
                        defaultActivity.decayPerTick(),
                        activity.baseline(),
                        activity.maximumMultiplier()
                )
                : activity;
        if (migratedSkill == skill
                && migratedLevels == levels
                && migratedActivity == activity) {
            return values;
        }
        return new Values(
                values.aptitude(),
                values.rareTalents(),
                values.inheritance(),
                values.career(),
                migratedSkill,
                migratedActivity,
                migratedLevels
        );
    }

    static VillagerPotentialConfig map(Values values) {
        ProfessionLevelThresholds thresholds = validateSection("levels", () ->
                new ProfessionLevelThresholds(
                        values.levels().novice(), values.levels().apprentice(),
                        values.levels().journeyman(), values.levels().expert(),
                        values.levels().master()
                )
        );
        AptitudeValues aptitude = values.aptitude();
        RareTalentValues rareTalents = values.rareTalents();
        InheritanceValues inheritance = values.inheritance();
        CareerValues career = values.career();
        SkillValues skill = values.skill();
        ActivityValues activity = values.activity();
        return new VillagerPotentialConfig(
                validateSection("aptitude", () -> new AptitudeGenerationConfig(
                                aptitude.enabled(), aptitude.minimum(), aptitude.maximum(),
                                aptitude.mean(), aptitude.variance(),
                                validateSection("rareTalents", () -> new RareTalentConfig(
                                        rareTalents.enabled(), rareTalents.chance(),
                                        rareTalents.strength()
                                ))
                        )
                ),
                validateSection("inheritance", () -> new AptitudeInheritanceConfig(
                                inheritance.enabled(), inheritance.inheritanceStrength(),
                                inheritance.randomContribution(), inheritance.mutationChance(),
                                inheritance.mutationVariance()
                        )
                ),
                validateSection("career", () -> new CareerProgressionConfig(
                                career.enabled(), career.adultsOnly(),
                                true, career.requireWorkActivity()
                        )
                ),
                validateSection("skill", () -> new SkillProgressionConfig(
                                skill.enabled(), skill.baseProgressionRate(),
                                skill.aptitudeInfluence(), skill.minimum(), skill.maximum(),
                                thresholds
                        )
                ),
                validateSection("activity", () -> new ProfessionActivityConfig(
                                activity.enabled(), DEFAULT_GAMEPLAY.activity().minimum(),
                                activity.baseline(), activity.maximumMultiplier(),
                                activity.gainPerSuccessfulTrade(), activity.decayRate()
                        )
                )
        );
    }

    static Values defaultValues() {
        VillagerPotentialConfig defaults = DEFAULT_GAMEPLAY;
        AptitudeGenerationConfig aptitude = defaults.aptitude();
        AptitudeInheritanceConfig inheritance = defaults.inheritance();
        CareerProgressionConfig career = defaults.career();
        SkillProgressionConfig skill = defaults.skill();
        ProfessionActivityConfig activity = defaults.activity();
        ProfessionLevelThresholds levels = skill.professionLevelThresholds();
        return new Values(
                new AptitudeValues(aptitude.enabled(), aptitude.mean(), aptitude.variance(), aptitude.minimum(), aptitude.maximum()),
                new RareTalentValues(aptitude.rareTalents().enabled(), aptitude.rareTalentChance(), aptitude.rareTalentStrength()),
                new InheritanceValues(inheritance.enabled(), inheritance.inheritanceStrength(), inheritance.randomContribution(), inheritance.mutationChance(), inheritance.mutationVariance()),
                new CareerValues(career.enabled(), career.adultsOnly(), career.requireJobSite(), career.requireWorkActivity()),
                new SkillValues(skill.enabled(), skill.progressionRate(), skill.aptitudeInfluence(), skill.minimumSkill(), skill.maximumSkill()),
                new ActivityValues(activity.enabled(), activity.increasePerTrade(), activity.decayPerTick(), activity.baseline(), activity.maximum()),
                new LevelValues(levels.noviceSkill(), levels.apprenticeSkill(), levels.journeymanSkill(), levels.expertSkill(), levels.masterSkill())
        );
    }

    public static VillagerTradeConfig tradeConfig() {
        return activeConfiguration.trades();
    }

    private static TradeValues currentTradeValues() {
        return new TradeValues(
                new SpecializationValues(
                        SPECIALIZATIONS_ENABLED.get(),
                        SPECIALIZATION_GLOBAL_STRENGTH.get(),
                        SPECIALIZATION_MINIMUM_BIAS.get(),
                        SPECIALIZATION_MAXIMUM_BIAS.get(),
                        SPECIALIZATION_CURVE_EXPONENT.get(),
                        List.copyOf(SPECIALIZATION_PROFESSION_OVERRIDES.get())
                ),
                new PaletteValues(
                        PALETTE_MODE.get(),
                        REPEATED_TRADE_PENALTY.get(),
                        MINIMUM_CANDIDATE_WEIGHT.get(),
                        MEMORY_DECAY_TICKS.get(),
                        RARE_TRADE_PROTECTION_ENABLED.get(),
                        RARE_TRADE_RECOVERY_TICKS.get(),
                        List.copyOf(RARE_TRADE_RESULTS.get()),
                        EXHAUSTION_RECOVERY_TICKS.get(),
                        CYCLE_RECOVERY_TICKS.get()
                ),
                new EconomyValues(
                        DEMAND_ENABLED.get(),
                        DEMAND_GAIN_PER_USE.get(),
                        DEMAND_DECAY_PER_TICK.get(),
                        DEMAND_MINIMUM.get(),
                        DEMAND_BASELINE.get(),
                        DEMAND_MAXIMUM.get(),
                        PRICE_INFLUENCE_ENABLED.get(),
                        MINIMUM_PRICE_MULTIPLIER.get(),
                        MAXIMUM_PRICE_MULTIPLIER.get(),
                        DEMAND_INFLUENCES_STOCK.get(),
                        STOCK_INFLUENCE_STRENGTH.get(),
                        MAXIMUM_ADDITIONAL_USES.get(),
                        MAXIMUM_USES_PER_OFFER.get()
                )
        );
    }

    static VillagerPotentialConfiguration validate(Values gameplay, TradeValues trades) {
        Objects.requireNonNull(gameplay, "gameplay");
        Objects.requireNonNull(trades, "trades");
        try {
            return new VillagerPotentialConfiguration(map(gameplay), mapTrade(trades));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException(
                    "Invalid Villager Potential server configuration: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    static VillagerPotentialConfiguration prepareValidatedConfiguration() {
        return SPEC.isLoaded()
                ? validate(currentValues(), currentTradeValues())
                : VillagerPotentialConfiguration.DEFAULT;
    }

    static VillagerPotentialConfiguration activeConfiguration() {
        return activeConfiguration;
    }

    static void activate(VillagerPotentialConfiguration configuration) {
        activeConfiguration = Objects.requireNonNull(configuration, "configuration");
    }

    static CompletableFuture<Void> reload(
            Supplier<? extends CompletableFuture<Void>> resourceReload
    ) {
        return reload(prepareValidatedConfiguration(), resourceReload);
    }

    static CompletableFuture<Void> reload(
            VillagerPotentialConfiguration candidate,
            Supplier<? extends CompletableFuture<Void>> resourceReload
    ) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(resourceReload, "resourceReload");
        final CompletableFuture<Void> resources;
        try {
            resources = Objects.requireNonNull(
                    resourceReload.get(),
                    "resourceReload result"
            );
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        return resources.thenRun(() -> activate(candidate));
    }

    static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            activate(prepareValidatedConfiguration());
        }
    }

    static VillagerTradeConfig mapTrade(TradeValues values) {
        SpecializationValues specialization = values.specializations();
        PaletteValues palette = values.palette();
        EconomyValues economy = values.economy();
        return new VillagerTradeConfig(
                validateSection("specializations", () -> new SpecializationConfig(
                        specialization.enabled(),
                        specialization.globalStrength(),
                        specialization.minimumBias(),
                        specialization.maximumBias(),
                        specialization.curveExponent(),
                        parseProfessionOverrides(specialization.professionOverrides())
                )),
                validateSection("palette/memory", () -> new TradePaletteConfig(
                        palette.mode(),
                        DEFAULT_TRADES.palette().maximumHistoryEntries(),
                        palette.repeatedTradePenalty(),
                        new TradeMemoryRecoveryConfig(
                                palette.memoryDecayTicks(),
                                palette.minimumCandidateWeight(),
                                palette.exhaustionRecoveryTicks(),
                                palette.cycleRecoveryTicks(),
                                palette.rareTradeRecoveryTicks()
                        ),
                        palette.rareTradeProtectionEnabled(),
                        parseNamespacedIds(palette.rareTradeResultItems())
                )),
                validateSection("economy", () -> new MarketEconomyConfig(
                        new MarketDemandConfig(
                                economy.demandEnabled(),
                                economy.demandMinimum(),
                                economy.demandBaseline(),
                                economy.demandMaximum(),
                                economy.demandGainPerUse(),
                                economy.demandDecayPerTick()
                        ),
                        new MarketDemandPriceConfig(
                                economy.priceEnabled(),
                                economy.minimumPriceMultiplier(),
                                economy.maximumPriceMultiplier()
                        ),
                        new MarketDemandStockConfig(
                                economy.stockEnabled(),
                                economy.stockInfluenceStrength(),
                                economy.maximumAdditionalUses(),
                                economy.maximumUsesPerOffer()
                        )
                ))
        );
    }

    static TradeValues defaultTradeValues() {
        SpecializationConfig specialization = DEFAULT_TRADES.specializations();
        TradePaletteConfig palette = DEFAULT_TRADES.palette();
        MarketEconomyConfig economy = DEFAULT_TRADES.economy();
        return new TradeValues(
                new SpecializationValues(
                        specialization.enabled(),
                        specialization.globalStrength(),
                        specialization.minimumBiasStrength(),
                        specialization.maximumBiasStrength(),
                        specialization.curveExponent(),
                        List.of()
                ),
                new PaletteValues(
                        palette.mode(),
                        palette.repeatedTradePenalty(),
                        palette.recovery().minimumCandidateWeight(),
                        palette.recovery().weightedPenaltyRecoveryTime(),
                        palette.rareTradeProtectionEnabled(),
                        palette.recovery().rareTradeRecoveryTime(),
                        List.copyOf(palette.rareTradeResultItems()),
                        palette.recovery().exhaustRecoveryTime(),
                        palette.recovery().cyclicResetTime()
                ),
                new EconomyValues(
                        economy.demand().enabled(),
                        economy.demand().increasePerPurchase(),
                        economy.demand().decayPerTick(),
                        economy.demand().minimum(),
                        economy.demand().baseline(),
                        economy.demand().maximum(),
                        economy.price().enabled(),
                        economy.price().minimumMultiplier(),
                        economy.price().maximumMultiplier(),
                        economy.stock().enabled(),
                        economy.stock().influenceStrength(),
                        economy.stock().maximumAdditionalUses(),
                        economy.stock().maximumUsesPerOffer()
                )
        );
    }

    public static MarketDemandStockConfig marketDemandStockConfig() {
        return tradeConfig().economy().stock();
    }

    public static boolean diagnosticLoggingEnabled() {
        return SPEC.isLoaded() && DIAGNOSTIC_LOGGING.get();
    }

    public static boolean detailedWeightLoggingEnabled() {
        return diagnosticLoggingEnabled() && DETAILED_WEIGHT_LOGGING.get();
    }

    private static ForgeConfigSpec.DoubleValue threshold(String name, double defaultValue, String levelName) {
        return BUILDER
                .comment("Inclusive 0.0-1000000.0 skill threshold for " + levelName + "; all five must be strictly ordered.")
                .defineInRange(name, defaultValue, 0.0, 1_000_000.0);
    }

    private static boolean validNamespacedId(Object value) {
        return value instanceof String id && ResourceLocation.tryParse(id) != null;
    }

    private static Map<ProfessionId, Double> parseProfessionOverrides(List<String> entries) {
        return SpecializationConfig.parseStrengthOverrides(entries);
    }

    private static Set<String> parseNamespacedIds(List<String> entries) {
        Set<String> ids = new LinkedHashSet<>();
        entries.stream().filter(ServerConfig::validNamespacedId).forEach(ids::add);
        return ids;
    }

    private static <T> T validateSection(String section, Supplier<T> factory) {
        try {
            return factory.get();
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException(
                    section + ": " + exception.getMessage(),
                    exception
            );
        }
    }

    record Values(AptitudeValues aptitude, RareTalentValues rareTalents, InheritanceValues inheritance, CareerValues career, SkillValues skill, ActivityValues activity, LevelValues levels) {
    }

    record AptitudeValues(boolean enabled, double mean, double variance, double minimum, double maximum) {
    }

    record RareTalentValues(boolean enabled, double chance, double strength) {
    }

    record InheritanceValues(boolean enabled, double inheritanceStrength, double randomContribution, double mutationChance, double mutationVariance) {
    }

    record CareerValues(boolean enabled, boolean adultsOnly, boolean requireJobSite, boolean requireWorkActivity) {
    }

    record SkillValues(boolean enabled, double baseProgressionRate, double aptitudeInfluence, double minimum, double maximum) {
    }

    record ActivityValues(boolean enabled, double gainPerSuccessfulTrade, double decayRate, double baseline, double maximumMultiplier) {
    }

    record LevelValues(double novice, double apprentice, double journeyman, double expert, double master) {
    }

    record TradeValues(
            SpecializationValues specializations,
            PaletteValues palette,
            EconomyValues economy
    ) {
    }

    record SpecializationValues(
            boolean enabled,
            double globalStrength,
            double minimumBias,
            double maximumBias,
            double curveExponent,
            List<String> professionOverrides
    ) {
    }

    record PaletteValues(
            TradePaletteRerollStrategy mode,
            double repeatedTradePenalty,
            double minimumCandidateWeight,
            long memoryDecayTicks,
            boolean rareTradeProtectionEnabled,
            long rareTradeRecoveryTicks,
            List<String> rareTradeResultItems,
            long exhaustionRecoveryTicks,
            long cycleRecoveryTicks
    ) {
    }

    record EconomyValues(
            boolean demandEnabled,
            double demandGainPerUse,
            double demandDecayPerTick,
            double demandMinimum,
            double demandBaseline,
            double demandMaximum,
            boolean priceEnabled,
            double minimumPriceMultiplier,
            double maximumPriceMultiplier,
            boolean stockEnabled,
            double stockInfluenceStrength,
            int maximumAdditionalUses,
            int maximumUsesPerOffer
    ) {
    }
}
