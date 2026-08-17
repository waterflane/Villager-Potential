package org.waterflane.villager_potential;

import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.VillagerPotentialConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerConfigTest {
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
    void meaningfulCustomValuesMapWithoutNeoForgeTypesEnteringCore() {
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
}
