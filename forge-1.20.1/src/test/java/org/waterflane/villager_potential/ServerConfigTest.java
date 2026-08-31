package org.waterflane.villager_potential;

import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.VillagerPotentialConfig;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.TradePaletteRerollStrategy;
import org.waterflane.villager_potential.core.VillagerTradeConfig;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerConfigTest {
    private static final ProfessionId LIBRARIAN = ProfessionId.parse("minecraft:librarian");

    @Test
    void defaultValuesMapToEnabledIntendedGameplay() {
        VillagerPotentialConfig mapped = ServerConfig.map(ServerConfig.defaultValues());

        assertEquals(VillagerPotentialConfig.DEFAULT, mapped);
        assertEquals(VillagerPotentialConfig.DEFAULT, ServerConfig.gameplayConfig());
        assertTrue(mapped.aptitude().enabled());
        assertTrue(mapped.aptitude().rareTalents().enabled());
        assertTrue(mapped.inheritance().enabled());
        assertTrue(mapped.career().enabled());
        assertTrue(mapped.skill().enabled());
        assertTrue(mapped.activity().enabled());
    }

    @Test
    void meaningfulCustomValuesMapWithoutForgeTypesEnteringCore() {
        VillagerPotentialConfig mapped = ServerConfig.map(customValues());

        assertEquals(1.2, mapped.aptitude().mean());
        assertEquals(0.16, mapped.aptitude().variance());
        assertFalse(mapped.aptitude().rareTalents().enabled());
        assertEquals(0.15, mapped.aptitude().rareTalentChance());
        assertEquals(4.0, mapped.aptitude().rareTalentStrength());
        assertEquals(0.6, mapped.inheritance().inheritanceStrength());
        assertEquals(0.25, mapped.inheritance().mutationChance());
        assertFalse(mapped.career().adultsOnly());
        assertTrue(mapped.career().requireJobSite());
        assertTrue(mapped.career().requireWorkActivity());
        assertEquals(0.002, mapped.skill().progressionRate());
        assertEquals(0.5, mapped.skill().aptitudeInfluence());
        assertEquals(0.2, mapped.activity().increasePerTrade());
        assertEquals(3.0, mapped.activity().maximum());
    }

    @Test
    void mapsAllFiveStrictlyOrderedProgressionThresholds() {
        var thresholds = ServerConfig.map(customValues()).skill().professionLevelThresholds();

        assertEquals(0.1, thresholds.noviceSkill());
        assertEquals(0.4, thresholds.apprenticeSkill());
        assertEquals(0.8, thresholds.journeymanSkill());
        assertEquals(1.2, thresholds.expertSkill());
        assertEquals(1.8, thresholds.masterSkill());
    }

    @Test
    void previousDefaultCurveMigratesWithoutOverwritingCustomAptitudeInfluence() {
        ServerConfig.Values defaults = ServerConfig.defaultValues();
        ServerConfig.Values legacy = new ServerConfig.Values(
                defaults.aptitude(),
                defaults.rareTalents(),
                defaults.inheritance(),
                defaults.career(),
                new ServerConfig.SkillValues(true, 0.00005, 0.35, 0.0, 5.0),
                new ServerConfig.ActivityValues(true, 0.1, 0.0001, 1.0, 2.0),
                new ServerConfig.LevelValues(0.0, 0.2, 0.5, 1.0, 5.0)
        );

        ServerConfig.Values migrated = ServerConfig.migrateLegacyDefaults(legacy);
        VillagerPotentialConfig mapped = ServerConfig.map(migrated);

        assertEquals(VillagerPotentialConfig.DEFAULT.skill().progressionRate(),
                mapped.skill().progressionRate());
        assertEquals(0.35, mapped.skill().aptitudeInfluence());
        assertEquals(VillagerPotentialConfig.DEFAULT.skill().maximumSkill(),
                mapped.skill().maximumSkill());
        assertEquals(VillagerPotentialConfig.DEFAULT.skill().professionLevelThresholds(),
                mapped.skill().professionLevelThresholds());
        assertEquals(0.0, mapped.activity().decayPerTick());
    }

    @Test
    void intermediateDefaultCurveMigratesToCurrentTickBasedDefaults() {
        ServerConfig.Values defaults = ServerConfig.defaultValues();
        ServerConfig.Values legacy = new ServerConfig.Values(
                defaults.aptitude(), defaults.rareTalents(), defaults.inheritance(),
                new ServerConfig.CareerValues(true, true, false, false),
                new ServerConfig.SkillValues(true, 0.001, 1.0, 0.0, 1.0),
                new ServerConfig.ActivityValues(true, 0.1, 0.0001, 1.0, 2.0),
                new ServerConfig.LevelValues(0.0, 0.2, 0.5, 0.8, 1.0)
        );

        VillagerPotentialConfig mapped = ServerConfig.map(
                ServerConfig.migrateLegacyDefaults(legacy)
        );

        assertEquals(VillagerPotentialConfig.DEFAULT.skill(), mapped.skill());
        assertEquals(0.0, mapped.activity().decayPerTick());
        assertTrue(mapped.career().requireJobSite());
    }

    @Test
    void rejectsRepresentativeCrossOptionBoundaryErrors() {
        ServerConfig.Values values = customValues();
        assertThrows(
                IllegalArgumentException.class,
                () -> ServerConfig.map(new ServerConfig.Values(
                        values.aptitude(), values.rareTalents(), values.inheritance(),
                        values.career(), values.skill(), values.activity(),
                        new ServerConfig.LevelValues(0.1, 0.4, 0.4, 1.2, 1.8)
                ))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ServerConfig.map(new ServerConfig.Values(
                        new ServerConfig.AptitudeValues(true, 3.0, 0.16, 0.4, 2.4),
                        values.rareTalents(), values.inheritance(), values.career(),
                        values.skill(), values.activity(), values.levels()
                ))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ServerConfig.map(new ServerConfig.Values(
                        values.aptitude(), values.rareTalents(),
                        new ServerConfig.InheritanceValues(true, 0.8, 0.3, 0.25, 0.04),
                        values.career(), values.skill(), values.activity(), values.levels()
                ))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ServerConfig.map(new ServerConfig.Values(
                        values.aptitude(), values.rareTalents(), values.inheritance(),
                        values.career(), values.skill(),
                        new ServerConfig.ActivityValues(true, 0.2, 0.0002, 2.0, 1.0),
                        values.levels()
                ))
        );
    }

    @Test
    void tradeDefaultsUsePersistentPaletteWithoutCompatibilityFallbacks() {
        VillagerTradeConfig mapped = ServerConfig.mapTrade(ServerConfig.defaultTradeValues());

        assertEquals(VillagerTradeConfig.DEFAULT, mapped);
        assertEquals(VillagerTradeConfig.DEFAULT, ServerConfig.tradeConfig());
        assertEquals(TradePaletteRerollStrategy.PERSISTENT, mapped.palette().mode());
        assertTrue(mapped.specializations().enabled());
        assertTrue(mapped.economy().demand().enabled());
        assertTrue(mapped.economy().price().enabled());
        assertEquals(0.10, mapped.economy().price().maximumEmeraldPaymentResultReduction());
        assertEquals(0.125, mapped.economy().price().maximumItemPaymentIncrease());
        assertEquals(8.0, mapped.economy().price().demandScoreForMaximumPrice());
    }

    @Test
    void everyPaletteModeMapsExactly() {
        for (TradePaletteRerollStrategy mode : TradePaletteRerollStrategy.values()) {
            assertEquals(mode, ServerConfig.mapTrade(tradeValues(
                    mode,
                    List.of(),
                    true,
                    true,
                    false
            )).palette().mode());
        }
    }

    @Test
    void namespacedProfessionOverridesApplyAndUnknownIdsRemainSafe() {
        VillagerTradeConfig mapped = ServerConfig.mapTrade(tradeValues(
                TradePaletteRerollStrategy.PERSISTENT,
                List.of(
                        "minecraft:librarian=0.4",
                        "othermod:unknown_profession=0.2"
                ),
                true,
                true,
                false
        ));

        assertEquals(0.4, mapped.specializations().strengthFor(LIBRARIAN));
        assertEquals(
                0.2,
                mapped.specializations().strengthFor(
                        ProfessionId.parse("othermod:unknown_profession")
                )
        );
        assertEquals(1.0, mapped.specializations().strengthFor(
                ProfessionId.parse("minecraft:farmer")
        ));
        assertEquals(2, mapped.specializations().professionStrengthOverrides().size());
    }

    @Test
    void priceAndStockTogglesMapIndependently() {
        VillagerTradeConfig priceOnly = ServerConfig.mapTrade(tradeValues(
                TradePaletteRerollStrategy.PERSISTENT,
                List.of(),
                true,
                true,
                false
        ));
        VillagerTradeConfig stockOnly = ServerConfig.mapTrade(tradeValues(
                TradePaletteRerollStrategy.PERSISTENT,
                List.of(),
                true,
                false,
                true
        ));

        assertTrue(priceOnly.economy().price().enabled());
        assertFalse(priceOnly.economy().stock().enabled());
        assertFalse(stockOnly.economy().price().enabled());
        assertTrue(stockOnly.economy().stock().enabled());
        assertTrue(stockOnly.economy().demand().enabled());
    }

    private static ServerConfig.Values customValues() {
        return new ServerConfig.Values(
                new ServerConfig.AptitudeValues(true, 1.2, 0.16, 0.4, 2.4),
                new ServerConfig.RareTalentValues(false, 0.15, 4.0),
                new ServerConfig.InheritanceValues(true, 0.6, 0.3, 0.25, 0.04),
                new ServerConfig.CareerValues(true, false, true, true),
                new ServerConfig.SkillValues(true, 0.002, 0.5, 0.0, 2.0),
                new ServerConfig.ActivityValues(true, 0.2, 0.0002, 0.8, 3.0),
                new ServerConfig.LevelValues(0.1, 0.4, 0.8, 1.2, 1.8)
        );
    }

    private static ServerConfig.TradeValues tradeValues(
            TradePaletteRerollStrategy mode,
            List<String> overrides,
            boolean demandEnabled,
            boolean priceEnabled,
            boolean stockEnabled
    ) {
        return new ServerConfig.TradeValues(
                new ServerConfig.SpecializationValues(
                        true,
                        1.0,
                        0.1,
                        1.0,
                        2.0,
                        overrides
                ),
                new ServerConfig.PaletteValues(
                        mode,
                        0.75,
                        0.01,
                        24_000L,
                        true,
                        1_200L,
                        List.of("minecraft:enchanted_book"),
                        24_000L,
                        48_000L
                ),
                new ServerConfig.EconomyValues(
                        demandEnabled,
                        2.0,
                        0.01,
                        -10.0,
                        0.0,
                        100.0,
                        priceEnabled,
                        0.8,
                        1.75,
                        stockEnabled,
                        0.5,
                        6,
                        24
                )
        );
    }
}
