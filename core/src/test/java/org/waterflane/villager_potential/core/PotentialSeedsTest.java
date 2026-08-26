package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PotentialSeedsTest {
    private static final UUID VILLAGER =
            UUID.fromString("00000000-0000-0000-0000-00000000c0de");
    private static final ProfessionId LIBRARIAN = ProfessionId.parse("minecraft:librarian");
    private static final ProfessionId FARMER = ProfessionId.parse("minecraft:farmer");

    @Test
    void seedsAreDeterministicForTheSameWorldAndVillager() {
        assertEquals(
                PotentialSeeds.initializationSeed(1_234L, VILLAGER),
                PotentialSeeds.initializationSeed(1_234L, VILLAGER)
        );
        assertEquals(
                PotentialSeeds.inheritanceSeed(1_234L, VILLAGER),
                PotentialSeeds.inheritanceSeed(1_234L, VILLAGER)
        );
        assertEquals(
                PotentialSeeds.specializationSeed(1_234L, VILLAGER, LIBRARIAN),
                PotentialSeeds.specializationSeed(1_234L, VILLAGER, LIBRARIAN)
        );
        assertEquals(
                PotentialSeeds.lazyAptitudeSeed(1_234L, VILLAGER, LIBRARIAN),
                PotentialSeeds.lazyAptitudeSeed(1_234L, VILLAGER, LIBRARIAN)
        );
    }

    @Test
    void differentWorldsVillagersAndProfessionsProduceDifferentSeeds() {
        UUID otherVillager = UUID.fromString("00000000-0000-0000-0000-00000000feed");

        assertNotEquals(
                PotentialSeeds.initializationSeed(1_234L, VILLAGER),
                PotentialSeeds.initializationSeed(5_678L, VILLAGER)
        );
        assertNotEquals(
                PotentialSeeds.initializationSeed(1_234L, VILLAGER),
                PotentialSeeds.initializationSeed(1_234L, otherVillager)
        );
        assertNotEquals(
                PotentialSeeds.inheritanceSeed(1_234L, VILLAGER),
                PotentialSeeds.initializationSeed(1_234L, VILLAGER)
        );
        assertNotEquals(
                PotentialSeeds.specializationSeed(1_234L, VILLAGER, LIBRARIAN),
                PotentialSeeds.specializationSeed(1_234L, VILLAGER, FARMER)
        );
        assertNotEquals(
                PotentialSeeds.specializationSeed(1_234L, VILLAGER, LIBRARIAN),
                PotentialSeeds.lazyAptitudeSeed(1_234L, VILLAGER, LIBRARIAN)
        );
    }

    @Test
    void rejectsMissingVillagerOrProfession() {
        assertThrows(
                NullPointerException.class,
                () -> PotentialSeeds.initializationSeed(1L, null)
        );
        assertThrows(
                NullPointerException.class,
                () -> PotentialSeeds.specializationSeed(1L, VILLAGER, null)
        );
    }
}
