package org.waterflane.villager_potential;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.waterflane.villager_potential.core.AptitudeGenerationConfig;
import org.waterflane.villager_potential.core.AptitudeInheritanceConfig;
import org.waterflane.villager_potential.core.CareerProgressionConfig;
import org.waterflane.villager_potential.core.MarketDemandStockConfig;
import org.waterflane.villager_potential.core.ProfessionActivityConfig;
import org.waterflane.villager_potential.core.ProfessionLevelThresholds;
import org.waterflane.villager_potential.core.RareTalentConfig;
import org.waterflane.villager_potential.core.SkillProgressionConfig;
import org.waterflane.villager_potential.core.VillagerPotentialConfig;

/** World-owned SERVER settings mapped into loader-neutral core configuration. */
public final class ServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    static final VillagerPotentialConfig DEFAULT_GAMEPLAY = VillagerPotentialConfig.DEFAULT;
    static final MarketDemandStockConfig DEFAULT_MARKET_DEMAND_STOCK = MarketDemandStockConfig.DISABLED;

    private static final ModConfigSpec.BooleanValue APTITUDE_ENABLED;
    private static final ModConfigSpec.DoubleValue APTITUDE_MEAN;
    private static final ModConfigSpec.DoubleValue APTITUDE_VARIANCE;
    private static final ModConfigSpec.DoubleValue APTITUDE_MINIMUM;
    private static final ModConfigSpec.DoubleValue APTITUDE_MAXIMUM;
    private static final ModConfigSpec.BooleanValue RARE_TALENTS_ENABLED;
    private static final ModConfigSpec.DoubleValue RARE_TALENT_CHANCE;
    private static final ModConfigSpec.DoubleValue RARE_TALENT_STRENGTH;
    private static final ModConfigSpec.BooleanValue INHERITANCE_ENABLED;
    private static final ModConfigSpec.DoubleValue INHERITANCE_STRENGTH;
    private static final ModConfigSpec.DoubleValue RANDOM_CONTRIBUTION;
    private static final ModConfigSpec.DoubleValue MUTATION_CHANCE;
    private static final ModConfigSpec.DoubleValue MUTATION_VARIANCE;
    private static final ModConfigSpec.BooleanValue CAREER_ENABLED;
    private static final ModConfigSpec.BooleanValue CAREER_ADULTS_ONLY;
    private static final ModConfigSpec.BooleanValue CAREER_REQUIRE_JOB_SITE;
    private static final ModConfigSpec.BooleanValue CAREER_REQUIRE_WORK_ACTIVITY;
    private static final ModConfigSpec.BooleanValue SKILL_ENABLED;
    private static final ModConfigSpec.DoubleValue SKILL_BASE_RATE;
    private static final ModConfigSpec.DoubleValue SKILL_APTITUDE_INFLUENCE;
    private static final ModConfigSpec.DoubleValue SKILL_MINIMUM;
    private static final ModConfigSpec.DoubleValue SKILL_MAXIMUM;
    private static final ModConfigSpec.BooleanValue ACTIVITY_ENABLED;
    private static final ModConfigSpec.DoubleValue ACTIVITY_GAIN_PER_TRADE;
    private static final ModConfigSpec.DoubleValue ACTIVITY_DECAY_RATE;
    private static final ModConfigSpec.DoubleValue ACTIVITY_BASELINE;
    private static final ModConfigSpec.DoubleValue ACTIVITY_MAXIMUM;
    private static final ModConfigSpec.DoubleValue LEVEL_NOVICE;
    private static final ModConfigSpec.DoubleValue LEVEL_APPRENTICE;
    private static final ModConfigSpec.DoubleValue LEVEL_JOURNEYMAN;
    private static final ModConfigSpec.DoubleValue LEVEL_EXPERT;
    private static final ModConfigSpec.DoubleValue LEVEL_MASTER;
    private static final ModConfigSpec.BooleanValue DEMAND_INFLUENCES_STOCK;
    private static final ModConfigSpec.IntValue MAXIMUM_ADDITIONAL_USES;
    private static final ModConfigSpec.IntValue MAXIMUM_USES_PER_OFFER;

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
        CAREER_REQUIRE_JOB_SITE = BUILDER
                .comment("Require a remembered valid job site for each loaded profession tick to count.")
                .define("requireJobSite", career.requireJobSite());
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

        BUILDER.push("marketDemand").push("stock");
        DEMAND_INFLUENCES_STOCK = BUILDER
                .comment(
                        "Allow Potential market demand to add uses after a vanilla-approved villager restock.",
                        "This never creates extra restocks or bypasses workstation and daily timing checks."
                )
                .define("enabled", DEFAULT_MARKET_DEMAND_STOCK.enabled());
        MAXIMUM_ADDITIONAL_USES = BUILDER
                .comment("Maximum uses demand may add to one offer per restock.")
                .defineInRange("maximumAdditionalUses", DEFAULT_MARKET_DEMAND_STOCK.maximumAdditionalUses(), 0, 64);
        MAXIMUM_USES_PER_OFFER = BUILDER
                .comment(
                        "Hard ceiling above which demand will not raise an offer's total uses.",
                        "Existing vanilla or modded offers above this value are never reduced."
                )
                .defineInRange("maximumUsesPerOffer", DEFAULT_MARKET_DEMAND_STOCK.maximumUsesPerOffer(), 1, 64);
        BUILDER.pop(2);
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    private ServerConfig() {
    }

    public static VillagerPotentialConfig gameplayConfig() {
        if (!SPEC.isLoaded()) {
            return DEFAULT_GAMEPLAY;
        }
        return map(new Values(
                new AptitudeValues(APTITUDE_ENABLED.get(), APTITUDE_MEAN.get(), APTITUDE_VARIANCE.get(), APTITUDE_MINIMUM.get(), APTITUDE_MAXIMUM.get()),
                new RareTalentValues(RARE_TALENTS_ENABLED.get(), RARE_TALENT_CHANCE.get(), RARE_TALENT_STRENGTH.get()),
                new InheritanceValues(INHERITANCE_ENABLED.get(), INHERITANCE_STRENGTH.get(), RANDOM_CONTRIBUTION.get(), MUTATION_CHANCE.get(), MUTATION_VARIANCE.get()),
                new CareerValues(CAREER_ENABLED.get(), CAREER_ADULTS_ONLY.get(), CAREER_REQUIRE_JOB_SITE.get(), CAREER_REQUIRE_WORK_ACTIVITY.get()),
                new SkillValues(SKILL_ENABLED.get(), SKILL_BASE_RATE.get(), SKILL_APTITUDE_INFLUENCE.get(), SKILL_MINIMUM.get(), SKILL_MAXIMUM.get()),
                new ActivityValues(ACTIVITY_ENABLED.get(), ACTIVITY_GAIN_PER_TRADE.get(), ACTIVITY_DECAY_RATE.get(), ACTIVITY_BASELINE.get(), ACTIVITY_MAXIMUM.get()),
                new LevelValues(LEVEL_NOVICE.get(), LEVEL_APPRENTICE.get(), LEVEL_JOURNEYMAN.get(), LEVEL_EXPERT.get(), LEVEL_MASTER.get())
        ));
    }

    static VillagerPotentialConfig map(Values values) {
        ProfessionLevelThresholds thresholds = new ProfessionLevelThresholds(
                values.levels().novice(), values.levels().apprentice(),
                values.levels().journeyman(), values.levels().expert(), values.levels().master()
        );
        AptitudeValues aptitude = values.aptitude();
        RareTalentValues rareTalents = values.rareTalents();
        InheritanceValues inheritance = values.inheritance();
        CareerValues career = values.career();
        SkillValues skill = values.skill();
        ActivityValues activity = values.activity();
        return new VillagerPotentialConfig(
                new AptitudeGenerationConfig(
                        aptitude.enabled(), aptitude.minimum(), aptitude.maximum(),
                        aptitude.mean(), aptitude.variance(),
                        new RareTalentConfig(rareTalents.enabled(), rareTalents.chance(), rareTalents.strength())
                ),
                new AptitudeInheritanceConfig(
                        inheritance.enabled(), inheritance.inheritanceStrength(),
                        inheritance.randomContribution(), inheritance.mutationChance(), inheritance.mutationVariance()
                ),
                new CareerProgressionConfig(
                        career.enabled(), career.adultsOnly(),
                        career.requireJobSite(), career.requireWorkActivity()
                ),
                new SkillProgressionConfig(
                        skill.enabled(), skill.baseProgressionRate(), skill.aptitudeInfluence(),
                        skill.minimum(), skill.maximum(), thresholds
                ),
                new ProfessionActivityConfig(
                        activity.enabled(), DEFAULT_GAMEPLAY.activity().minimum(),
                        activity.baseline(), activity.maximumMultiplier(),
                        activity.gainPerSuccessfulTrade(), activity.decayRate()
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

    public static MarketDemandStockConfig marketDemandStockConfig() {
        return new MarketDemandStockConfig(
                DEMAND_INFLUENCES_STOCK.get(),
                MAXIMUM_ADDITIONAL_USES.get(),
                MAXIMUM_USES_PER_OFFER.get()
        );
    }

    private static ModConfigSpec.DoubleValue threshold(String name, double defaultValue, String levelName) {
        return BUILDER
                .comment("Inclusive 0.0-1000000.0 skill threshold for " + levelName + "; all five must be strictly ordered.")
                .defineInRange(name, defaultValue, 0.0, 1_000_000.0);
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
}
