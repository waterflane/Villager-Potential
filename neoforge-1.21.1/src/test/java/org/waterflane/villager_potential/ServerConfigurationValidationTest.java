package org.waterflane.villager_potential;

import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.TradePaletteRerollStrategy;
import org.waterflane.villager_potential.core.VillagerPotentialConfiguration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerConfigurationValidationTest {
    @Test
    void validDefaultsProduceTheCompleteLoaderNeutralSnapshot() {
        assertEquals(
                VillagerPotentialConfiguration.DEFAULT,
                ServerConfig.validate(
                        ServerConfig.defaultValues(),
                        ServerConfig.defaultTradeValues()
                )
        );
    }

    @Test
    void rejectsInvalidIdentityInheritanceAndProgressionSections() {
        ServerConfig.Values gameplay = gameplay();
        ServerConfig.TradeValues trades = trades();

        assertInvalid(new ServerConfig.Values(
                new ServerConfig.AptitudeValues(true, Double.NaN, 0.16, 0.4, 2.4),
                gameplay.rareTalents(), gameplay.inheritance(), gameplay.career(),
                gameplay.skill(), gameplay.activity(), gameplay.levels()
        ), trades);
        assertInvalid(new ServerConfig.Values(
                gameplay.aptitude(),
                new ServerConfig.RareTalentValues(true, 1.01, 4.0),
                gameplay.inheritance(), gameplay.career(), gameplay.skill(),
                gameplay.activity(), gameplay.levels()
        ), trades);
        assertInvalid(new ServerConfig.Values(
                gameplay.aptitude(), gameplay.rareTalents(),
                new ServerConfig.InheritanceValues(true, -0.1, 0.3, 0.25, 0.04),
                gameplay.career(), gameplay.skill(), gameplay.activity(), gameplay.levels()
        ), trades);
        assertInvalid(new ServerConfig.Values(
                gameplay.aptitude(), gameplay.rareTalents(), gameplay.inheritance(),
                null, gameplay.skill(), gameplay.activity(), gameplay.levels()
        ), trades);
        assertInvalid(new ServerConfig.Values(
                gameplay.aptitude(), gameplay.rareTalents(), gameplay.inheritance(),
                gameplay.career(),
                new ServerConfig.SkillValues(true, -0.001, 0.5, 0.0, 2.0),
                gameplay.activity(), gameplay.levels()
        ), trades);
        assertInvalid(new ServerConfig.Values(
                gameplay.aptitude(), gameplay.rareTalents(), gameplay.inheritance(),
                gameplay.career(), gameplay.skill(),
                new ServerConfig.ActivityValues(true, 0.2, 0.0002, 3.0, 2.0),
                gameplay.levels()
        ), trades);
        assertInvalid(new ServerConfig.Values(
                gameplay.aptitude(), gameplay.rareTalents(), gameplay.inheritance(),
                gameplay.career(), gameplay.skill(), gameplay.activity(),
                new ServerConfig.LevelValues(0.1, 0.4, 0.4, 1.2, 1.8)
        ), trades);
    }

    @Test
    void rejectsInvalidSpecializationPaletteAndMemorySections() {
        ServerConfig.Values gameplay = gameplay();
        ServerConfig.TradeValues trades = trades();

        assertInvalid(gameplay, new ServerConfig.TradeValues(
                new ServerConfig.SpecializationValues(
                        true, 1.0, 0.8, 0.2, 2.0, List.of()
                ),
                trades.palette(), trades.economy()
        ));
        assertInvalid(gameplay, withOverrides(List.of("not_namespaced=0.5")));
        assertInvalid(gameplay, withOverrides(List.of(
                "minecraft:librarian=0.5",
                "minecraft:librarian=0.7"
        )));
        assertInvalid(gameplay, withOverrides(List.of("minecraft:librarian=Infinity")));
        assertInvalid(gameplay, withPalette(new ServerConfig.PaletteValues(
                null, 0.75, 0.01, 24_000L, true, 1_200L,
                List.of(), 24_000L, 48_000L
        )));
        assertInvalid(gameplay, withPalette(new ServerConfig.PaletteValues(
                TradePaletteRerollStrategy.WEIGHTED_MEMORY,
                1.01, 0.01, 24_000L, true, 1_200L,
                List.of(), 24_000L, 48_000L
        )));
        assertInvalid(gameplay, withPalette(new ServerConfig.PaletteValues(
                TradePaletteRerollStrategy.WEIGHTED_MEMORY,
                0.75, -0.01, 24_000L, true, 1_200L,
                List.of(), 24_000L, 48_000L
        )));
    }

    @Test
    void rejectsImpossibleDemandPriceAndStockSections() {
        ServerConfig.Values gameplay = gameplay();
        ServerConfig.TradeValues trades = trades();

        assertInvalid(gameplay, withEconomy(new ServerConfig.EconomyValues(
                true, 2.0, 0.01, 10.0, 0.0, 100.0,
                true, 0.8, 1.75, false, 0.5, 6, 24
        )));
        assertInvalid(gameplay, withEconomy(new ServerConfig.EconomyValues(
                true, 2.0, 0.01, -10.0, 0.0, 100.0,
                true, 0.0, 1.75, false, 0.5, 6, 24
        )));
        assertInvalid(gameplay, withEconomy(new ServerConfig.EconomyValues(
                true, 2.0, 0.01, -10.0, 0.0, 100.0,
                false, 0.8, 1.75, true, 0.5, -1, 24
        )));
        assertInvalid(gameplay, new ServerConfig.TradeValues(
                trades.specializations(), trades.palette(),
                new ServerConfig.EconomyValues(
                        true, 2.0, Double.POSITIVE_INFINITY,
                        -10.0, 0.0, 100.0,
                        false, 0.8, 1.75, true, 0.5, 6, 24
                )
        ));
    }

    private static void assertInvalid(
            ServerConfig.Values gameplay,
            ServerConfig.TradeValues trades
    ) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ServerConfig.validate(gameplay, trades)
        );
        assertTrue(exception.getMessage().startsWith(
                "Invalid Villager Potential server configuration:"
        ));
    }

    private static ServerConfig.Values gameplay() {
        return new ServerConfig.Values(
                new ServerConfig.AptitudeValues(true, 1.2, 0.16, 0.4, 2.4),
                new ServerConfig.RareTalentValues(true, 0.15, 4.0),
                new ServerConfig.InheritanceValues(true, 0.6, 0.3, 0.25, 0.04),
                new ServerConfig.CareerValues(true, false, true, true),
                new ServerConfig.SkillValues(true, 0.002, 0.5, 0.0, 2.0),
                new ServerConfig.ActivityValues(true, 0.2, 0.0002, 0.8, 3.0),
                new ServerConfig.LevelValues(0.1, 0.4, 0.8, 1.2, 1.8)
        );
    }

    private static ServerConfig.TradeValues trades() {
        return new ServerConfig.TradeValues(
                new ServerConfig.SpecializationValues(
                        true, 1.0, 0.1, 1.0, 2.0, List.of()
                ),
                new ServerConfig.PaletteValues(
                        TradePaletteRerollStrategy.PERSISTENT,
                        0.75, 0.01, 24_000L, true, 1_200L,
                        List.of("minecraft:enchanted_book"), 24_000L, 48_000L
                ),
                new ServerConfig.EconomyValues(
                        true, 2.0, 0.01, -10.0, 0.0, 100.0,
                        true, 0.8, 1.75, false, 0.5, 6, 24
                )
        );
    }

    private static ServerConfig.TradeValues withOverrides(List<String> overrides) {
        ServerConfig.TradeValues trades = trades();
        return new ServerConfig.TradeValues(
                new ServerConfig.SpecializationValues(
                        true, 1.0, 0.1, 1.0, 2.0, overrides
                ),
                trades.palette(), trades.economy()
        );
    }

    private static ServerConfig.TradeValues withPalette(ServerConfig.PaletteValues palette) {
        ServerConfig.TradeValues trades = trades();
        return new ServerConfig.TradeValues(
                trades.specializations(), palette, trades.economy()
        );
    }

    private static ServerConfig.TradeValues withEconomy(ServerConfig.EconomyValues economy) {
        ServerConfig.TradeValues trades = trades();
        return new ServerConfig.TradeValues(
                trades.specializations(), trades.palette(), economy
        );
    }
}
