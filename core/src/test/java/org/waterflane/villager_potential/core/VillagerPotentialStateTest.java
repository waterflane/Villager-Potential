package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerPotentialStateTest {
    @Test
    void createsDefaultStateAtCurrentSchemaVersion() {
        assertEquals(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                VillagerPotentialState.createDefault().schemaVersion()
        );
    }

    @Test
    void requiresAPositiveSchemaVersion() {
        assertThrows(IllegalArgumentException.class, () -> new VillagerPotentialState(0));
    }

    @Test
    void usesValueEquality() {
        VillagerPotentialState state = VillagerPotentialState.createDefault();

        assertEquals(state, new VillagerPotentialState(state.schemaVersion(), Map.of()));
    }

    @Test
    void storesIndependentAptitudesPerProfession() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        ProfessionId engineer = ProfessionId.parse("example_mod:engineer");

        VillagerPotentialState state = VillagerPotentialState.createDefault()
                .withAptitude(librarian, 0.75)
                .withAptitude(engineer, 1.25);

        assertEquals(0.75, state.aptitudeFor(librarian).orElseThrow());
        assertEquals(1.25, state.aptitudeFor(engineer).orElseThrow());
    }

    @Test
    void multipleProfessionCareersCoexist() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        ProfessionId farmer = ProfessionId.parse("minecraft:farmer");

        VillagerPotentialState state = VillagerPotentialState.createDefault()
                .assignProfession(librarian, 100L)
                .withCareer(
                        librarian,
                        new ProfessionCareerState(40L, 0.25, 100L, 100L)
                )
                .assignProfession(farmer, 200L);

        assertEquals(2, state.careers().size());
        assertEquals(40L, state.careerFor(librarian).orElseThrow().accumulatedProfessionTime());
        assertEquals(ProfessionCareerState.firstAssignedAt(200L), state.careerFor(farmer).orElseThrow());
        assertEquals(farmer, state.activeProfession().orElseThrow());
    }

    @Test
    void returningToPreviousProfessionRestoresItsCareerRecord() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        ProfessionId farmer = ProfessionId.parse("minecraft:farmer");
        ProfessionCareerState learnedCareer = new ProfessionCareerState(80L, 0.75, 100L, 100L);

        VillagerPotentialState state = VillagerPotentialState.createDefault()
                .assignProfession(librarian, 100L)
                .withCareer(librarian, learnedCareer)
                .assignProfession(farmer, 200L)
                .assignProfession(librarian, 300L);

        ProfessionCareerState restored = state.careerFor(librarian).orElseThrow();
        assertEquals(80L, restored.accumulatedProfessionTime());
        assertEquals(0.75, restored.learnedSkill());
        assertEquals(100L, restored.firstAssignment());
        assertEquals(300L, restored.latestAssignment());
        assertEquals(librarian, state.activeProfession().orElseThrow());
        assertTrue(state.careerFor(farmer).isPresent());
    }

    @Test
    void accumulatesOnlyTheActiveProfessionAndSaturatesSafely() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        ProfessionId farmer = ProfessionId.parse("minecraft:farmer");
        VillagerPotentialState state = VillagerPotentialState.createDefault()
                .assignProfession(librarian, 100L)
                .withCareer(
                        librarian,
                        new ProfessionCareerState(Long.MAX_VALUE - 1L, 0.5, 100L, 100L)
                )
                .assignProfession(farmer, 200L)
                .accumulateActiveProfessionTime(20L);

        assertEquals(
                Long.MAX_VALUE - 1L,
                state.careerFor(librarian).orElseThrow().accumulatedProfessionTime()
        );
        assertEquals(20L, state.careerFor(farmer).orElseThrow().accumulatedProfessionTime());

        VillagerPotentialState saturated = state
                .assignProfession(librarian, 300L)
                .accumulateActiveProfessionTime(20L);
        assertEquals(
                Long.MAX_VALUE,
                saturated.careerFor(librarian).orElseThrow().accumulatedProfessionTime()
        );
    }

    @Test
    void skillRemainsSeparateFromAptitude() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");

        VillagerPotentialState state = VillagerPotentialState.createDefault()
                .withAptitude(librarian, 1.25)
                .assignProfession(librarian, 100L)
                .withCareer(
                        librarian,
                        ProfessionCareerState.firstAssignedAt(100L).withLearnedSkill(0.5)
                );

        assertEquals(1.25, state.aptitudeFor(librarian).orElseThrow());
        assertEquals(0.5, state.careerFor(librarian).orElseThrow().learnedSkill());
    }

    @Test
    void activeProfessionProgressUsesItsAptitudeAndLeavesInactiveCareerUnchanged() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        ProfessionId farmer = ProfessionId.parse("minecraft:farmer");
        ProfessionCareerState farmerCareer = new ProfessionCareerState(40L, 0.2, 5L, 5L);
        SkillProgressionConfig progression = new SkillProgressionConfig(
                0.001,
                0.0,
                1.0,
                List.of(0.2, 0.5, 0.8, 1.0)
        );
        VillagerPotentialState state = new VillagerPotentialState(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                Map.of(librarian, 1.5, farmer, 0.5)
        ).assignProfession(farmer, 5L)
                .withCareer(farmer, farmerCareer)
                .assignProfession(librarian, 10L);

        VillagerPotentialState progressed = state.progressActiveProfession(20L, progression);

        assertEquals(0.03, progressed.careerFor(librarian).orElseThrow().learnedSkill(), 0.000_000_1);
        assertEquals(farmerCareer, progressed.careerFor(farmer).orElseThrow());
    }

    @Test
    void inactiveTradingBaselineStillProgressesAndActivityCapLimitsAcceleration() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        SkillProgressionConfig progression = new SkillProgressionConfig(
                0.001,
                0.0,
                1.0,
                List.of(0.2, 0.5, 0.8, 1.0)
        );
        ProfessionActivityConfig activity = new ProfessionActivityConfig(
                0.5,
                1.0,
                1.5,
                0.25,
                0.001
        );
        VillagerPotentialState inactive = new VillagerPotentialState(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                Map.of(librarian, 1.0)
        ).assignProfession(librarian, 0L);
        VillagerPotentialState active = inactive;
        for (int trade = 0; trade < 20; trade++) {
            active = active.recordProfessionTrade(librarian, 100L, activity);
        }

        VillagerPotentialState inactiveProgress = inactive.progressActiveProfession(
                100L,
                100L,
                progression,
                activity
        );
        VillagerPotentialState activeProgress = active.progressActiveProfession(
                100L,
                100L,
                progression,
                activity
        );

        assertEquals(
                0.1,
                inactiveProgress.careerFor(librarian).orElseThrow().learnedSkill(),
                0.000_000_1
        );
        assertEquals(
                0.15,
                activeProgress.careerFor(librarian).orElseThrow().learnedSkill(),
                0.000_000_1
        );
    }

    @Test
    void missingProfessionHasNoGeneratedAptitude() {
        VillagerPotentialState state = VillagerPotentialState.createDefault();

        assertFalse(state.aptitudeFor(ProfessionId.parse("minecraft:farmer")).isPresent());
        assertTrue(state.aptitudes().isEmpty());
    }

    @Test
    void protectsStoredAptitudesFromExternalMutation() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        Map<ProfessionId, Double> aptitudes = new HashMap<>();
        aptitudes.put(librarian, 0.75);

        VillagerPotentialState state = new VillagerPotentialState(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                aptitudes
        );
        aptitudes.put(librarian, 1.5);

        assertEquals(0.75, state.aptitudeFor(librarian).orElseThrow());
        assertThrows(
                UnsupportedOperationException.class,
                () -> state.aptitudes().put(librarian, 1.5)
        );
    }

    @Test
    void currentSchemaDoesNotRequireMigration() {
        VillagerPotentialState state = VillagerPotentialState.createDefault();

        assertEquals(state, VillagerPotentialState.migrate(state.schemaVersion()));
    }

    @Test
    void migratesSyntheticVersionZero() {
        assertEquals(VillagerPotentialState.createDefault(), VillagerPotentialState.migrate(0));
    }

    @Test
    void migratesVersionOneWithoutGeneratingAptitudes() {
        assertEquals(VillagerPotentialState.createDefault(), VillagerPotentialState.migrate(1));
    }

    @Test
    void migratesVersionTwoWithoutInventingCareerHistory() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");

        VillagerPotentialState migrated = VillagerPotentialState.migrate(
                2,
                Map.of(librarian, 0.75)
        );

        assertEquals(0.75, migrated.aptitudeFor(librarian).orElseThrow());
        assertTrue(migrated.careers().isEmpty());
        assertTrue(migrated.activeProfession().isEmpty());
        assertEquals(VillagerPotentialState.CURRENT_SCHEMA_VERSION, migrated.schemaVersion());
    }

    @Test
    void migratesVersionThreeWithoutInventingTradeActivity() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        ProfessionCareerState career = ProfessionCareerState.firstAssignedAt(100L)
                .withLearnedSkill(0.4);

        VillagerPotentialState migrated = VillagerPotentialState.migrate(
                3,
                Map.of(librarian, 1.25),
                Map.of(librarian, career),
                java.util.Optional.of(librarian)
        );

        assertEquals(career, migrated.careerFor(librarian).orElseThrow());
        assertTrue(migrated.professionActivities().isEmpty());
        assertEquals(VillagerPotentialState.CURRENT_SCHEMA_VERSION, migrated.schemaVersion());
    }

    @Test
    void migratesVersionFourWithoutReusingNormalizedActivityAsAMultiplier() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        ProfessionCareerState career = ProfessionCareerState.firstAssignedAt(100L)
                .withLearnedSkill(0.4);

        VillagerPotentialState migrated = VillagerPotentialState.migrate(
                4,
                Map.of(librarian, 1.25),
                Map.of(librarian, career),
                java.util.Optional.of(librarian),
                Map.of(librarian, new ProfessionActivityState(0.5, 100L))
        );

        assertEquals(career, migrated.careerFor(librarian).orElseThrow());
        assertTrue(migrated.professionActivities().isEmpty());
        assertEquals(VillagerPotentialState.CURRENT_SCHEMA_VERSION, migrated.schemaVersion());
    }

    @Test
    void rejectsUnknownNewerSchemaVersion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> VillagerPotentialState.migrate(VillagerPotentialState.CURRENT_SCHEMA_VERSION + 1)
        );
    }
}
