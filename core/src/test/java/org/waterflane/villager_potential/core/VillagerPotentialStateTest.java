package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    void supportedSkillMutationPreservesCareerTenureAndRejectsInvalidValues() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        ProfessionCareerState career = new ProfessionCareerState(4_000L, 0.75, 100L, 300L);
        VillagerPotentialState state = VillagerPotentialState.createDefault()
                .assignProfession(librarian, 100L)
                .withCareer(librarian, career);

        VillagerPotentialState updated = state.withSkill(librarian, 2.5);

        ProfessionCareerState updatedCareer = updated.careerFor(librarian).orElseThrow();
        assertEquals(2.5, updatedCareer.learnedSkill());
        assertEquals(4_000L, updatedCareer.accumulatedProfessionTime());
        assertEquals(100L, updatedCareer.firstAssignment());
        assertEquals(300L, updatedCareer.latestAssignment());
        assertThrows(IllegalArgumentException.class, () -> state.withSkill(librarian, -1.0));
        assertThrows(IllegalArgumentException.class, () -> state.withSkill(librarian, Double.NaN));
        assertThrows(
                IllegalStateException.class,
                () -> state.withSkill(ProfessionId.parse("example:missing"), 1.0)
        );
    }

    @Test
    void targetedDerivedResetPreservesAptitudesAndUnrelatedProfessions() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        ProfessionId farmer = ProfessionId.parse("minecraft:farmer");
        TradeKey paper = trade("minecraft:paper", "minecraft:emerald");
        TradeKey wheat = trade("minecraft:wheat", "minecraft:emerald");
        VillagerPotentialState state = VillagerPotentialState.createDefault()
                .withAptitude(librarian, 1.25)
                .withAptitude(farmer, 0.75)
                .assignProfession(farmer, 50L)
                .recordPresentedTrades(farmer, List.of(wheat), List.of(wheat), 60L, 16)
                .recordTradePurchase(farmer, wheat, 70L)
                .assignProfession(librarian, 100L)
                .recordProfessionTrade(
                        librarian,
                        110L,
                        VillagerPotentialConfig.DEFAULT.activity()
                )
                .recordPresentedTrades(librarian, List.of(paper), List.of(paper), 120L, 16)
                .recordTradePurchase(librarian, paper, 130L);

        VillagerPotentialState reset = state.resetProfessionDerivedState(librarian);

        assertEquals(1.25, reset.aptitudeFor(librarian).orElseThrow());
        assertTrue(reset.careerFor(librarian).isEmpty());
        assertFalse(reset.professionActivities().containsKey(librarian));
        assertTrue(reset.tradePaletteFor(librarian).isEmpty());
        assertFalse(reset.marketDemand().containsKey(librarian));
        assertTrue(reset.activeProfession().isEmpty());
        assertEquals(state.careerFor(farmer), reset.careerFor(farmer));
        assertEquals(state.tradePaletteFor(farmer), reset.tradePaletteFor(farmer));
        assertEquals(state.marketDemand().get(farmer), reset.marketDemand().get(farmer));
        assertEquals(0.75, reset.aptitudeFor(farmer).orElseThrow());
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
    void storesIndependentTradePalettesPerProfession() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        ProfessionId farmer = ProfessionId.parse("minecraft:farmer");
        TradePaletteState librarianPalette = new TradePaletteState(
                List.of(trade("minecraft:paper", "minecraft:emerald")),
                List.of(trade("minecraft:book", "minecraft:emerald"))
        );
        TradePaletteState farmerPalette = new TradePaletteState(
                List.of(trade("minecraft:wheat", "minecraft:emerald")),
                List.of()
        );

        VillagerPotentialState state = VillagerPotentialState.createDefault()
                .withTradePalette(librarian, librarianPalette)
                .withTradePalette(farmer, farmerPalette);

        assertEquals(librarianPalette, state.tradePaletteFor(librarian).orElseThrow());
        assertEquals(farmerPalette, state.tradePaletteFor(farmer).orElseThrow());
        assertEquals(2, state.tradePalettes().size());
    }

    @Test
    void professionSwitchRestoresItsTradePaletteWithoutChangingHistory() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        ProfessionId farmer = ProfessionId.parse("minecraft:farmer");
        TradePaletteState librarianPalette = new TradePaletteState(
                List.of(trade("minecraft:paper", "minecraft:emerald")),
                List.of(trade("minecraft:book", "minecraft:emerald"))
        );
        TradePaletteState farmerPalette = new TradePaletteState(
                List.of(trade("minecraft:potato", "minecraft:emerald")),
                List.of(trade("minecraft:wheat", "minecraft:emerald"))
        );

        VillagerPotentialState state = VillagerPotentialState.createDefault()
                .assignProfession(librarian, 100L)
                .withTradePalette(librarian, librarianPalette)
                .assignProfession(farmer, 200L)
                .withTradePalette(farmer, farmerPalette)
                .assignProfession(librarian, 300L);

        assertEquals(librarian, state.activeProfession().orElseThrow());
        assertEquals(librarianPalette, state.tradePaletteFor(librarian).orElseThrow());
        assertEquals(farmerPalette, state.tradePaletteFor(farmer).orElseThrow());
    }

    @Test
    void offerHistoryIsIsolatedByProfession() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        ProfessionId farmer = ProfessionId.parse("minecraft:farmer");
        TradeKey sharedTrade = trade("minecraft:paper", "minecraft:emerald");

        VillagerPotentialState state = VillagerPotentialState.createDefault()
                .recordPresentedTrades(
                        librarian,
                        List.of(sharedTrade),
                        List.of(sharedTrade),
                        100L,
                        16
                )
                .recordPresentedTrades(
                        farmer,
                        List.of(sharedTrade),
                        List.of(sharedTrade),
                        200L,
                        16
                )
                .recordTradeUse(farmer, sharedTrade, 220L, 16);

        TradeHistory librarianHistory = state.tradePaletteFor(librarian)
                .orElseThrow().offerHistory().get(sharedTrade);
        TradeHistory farmerHistory = state.tradePaletteFor(farmer)
                .orElseThrow().offerHistory().get(sharedTrade);
        assertEquals(0L, librarianHistory.timesUsed());
        assertEquals(1L, farmerHistory.timesUsed());
        assertEquals(java.util.OptionalLong.of(100L), librarianHistory.lastSeen());
        assertEquals(java.util.OptionalLong.of(200L), farmerHistory.lastSeen());
    }

    @Test
    void presentingAnOfferDoesNotCreateMarketDemand() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        TradeKey trade = trade("minecraft:emerald", "minecraft:book");

        VillagerPotentialState state = VillagerPotentialState.createDefault()
                .recordPresentedTrades(
                        librarian,
                        List.of(trade),
                        List.of(trade),
                        100L,
                        16
                );

        assertTrue(state.marketDemandFor(librarian, trade).isEmpty());
        assertTrue(state.marketDemand().isEmpty());
    }

    @Test
    void marketDemandIsIsolatedByTradeAndProfessionAndDoesNotCreateActivity() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        ProfessionId farmer = ProfessionId.parse("minecraft:farmer");
        TradeKey paper = trade("minecraft:paper", "minecraft:emerald");
        TradeKey book = trade("minecraft:emerald", "minecraft:book");

        VillagerPotentialState librarianPurchase = VillagerPotentialState.createDefault()
                .recordTradePurchase(librarian, paper, 100L);

        assertEquals(
                MarketDemandState.firstPurchaseAt(100L),
                librarianPurchase.marketDemandFor(librarian, paper).orElseThrow()
        );
        assertTrue(librarianPurchase.marketDemandFor(librarian, book).isEmpty());
        assertTrue(librarianPurchase.marketDemandFor(farmer, paper).isEmpty());
        assertTrue(librarianPurchase.professionActivities().isEmpty());
        assertTrue(librarianPurchase.tradePalettes().isEmpty());

        VillagerPotentialState farmerPurchase = librarianPurchase
                .recordTradePurchase(farmer, paper, 200L);
        assertEquals(
                MarketDemandState.firstPurchaseAt(100L),
                farmerPurchase.marketDemandFor(librarian, paper).orElseThrow()
        );
        assertEquals(
                MarketDemandState.firstPurchaseAt(200L),
                farmerPurchase.marketDemandFor(farmer, paper).orElseThrow()
        );
    }

    @Test
    void marketDemandReadsDecayLazilyWithoutRewritingStoredTrades() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        TradeKey paper = trade("minecraft:paper", "minecraft:emerald");
        MarketDemandConfig config = new MarketDemandConfig(0.0, 0.0, 10.0, 2.0, 0.25);
        VillagerPotentialState state = VillagerPotentialState.createDefault()
                .recordTradePurchase(librarian, paper, 100L, config);
        Map<ProfessionId, Map<TradeKey, MarketDemandState>> storedDemand = state.marketDemand();

        assertEquals(
                1.0,
                state.marketDemandScoreFor(librarian, paper, 104L, config).orElseThrow()
        );
        assertEquals(storedDemand, state.marketDemand());
        assertEquals(
                1.0,
                state.marketDemandScoreFor(librarian, paper, 104L, config).orElseThrow()
        );
    }

    @Test
    void completedSleepClearsDemandWithoutTouchingOtherVillagerState() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        ProfessionId farmer = ProfessionId.parse("minecraft:farmer");
        TradeKey paper = trade("minecraft:paper", "minecraft:emerald");
        VillagerPotentialState state = VillagerPotentialState.createDefault()
                .assignProfession(librarian, 10L)
                .recordTradePurchase(librarian, paper, 100L)
                .recordTradePurchase(farmer, paper, 120L);

        VillagerPotentialState reset = state.resetMarketDemandAfterSleep();

        assertTrue(reset.marketDemand().isEmpty());
        assertEquals(state.aptitudes(), reset.aptitudes());
        assertEquals(state.careers(), reset.careers());
        assertEquals(state.activeProfession(), reset.activeProfession());
        assertEquals(state.tradePalettes(), reset.tradePalettes());
        assertSame(reset, reset.resetMarketDemandAfterSleep());
    }

    @Test
    void storesOneStableSpecializationPerProfessionCareer() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        SpecializationId enchanter =
                SpecializationId.parse("villager_potential:librarian/enchanter");
        SpecializationId cartographer =
                SpecializationId.parse("villager_potential:librarian/cartographer");

        VillagerPotentialState state = VillagerPotentialState.createDefault()
                .assignProfession(librarian, 100L)
                .withSpecialization(librarian, enchanter);

        assertEquals(enchanter, state.specializationFor(librarian).orElseThrow());
        assertEquals(state, state.withSpecialization(librarian, enchanter));
        assertThrows(
                IllegalStateException.class,
                () -> state.withSpecialization(librarian, cartographer)
        );
    }

    @Test
    void multipleCareerSpecializationsCoexistAcrossProfessionChanges() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        ProfessionId farmer = ProfessionId.parse("minecraft:farmer");
        SpecializationId enchanter =
                SpecializationId.parse("villager_potential:librarian/enchanter");
        SpecializationId horticulturist =
                SpecializationId.parse("villager_potential:farmer/horticulturist");

        VillagerPotentialState state = VillagerPotentialState.createDefault()
                .assignProfession(librarian, 100L)
                .withSpecialization(librarian, enchanter)
                .assignProfession(farmer, 200L)
                .withSpecialization(farmer, horticulturist)
                .assignProfession(librarian, 300L);

        assertEquals(enchanter, state.specializationFor(librarian).orElseThrow());
        assertEquals(horticulturist, state.specializationFor(farmer).orElseThrow());
        assertEquals(librarian, state.activeProfession().orElseThrow());
    }

    @Test
    void specializationStorageRequiresAnExistingCareer() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        SpecializationId enchanter =
                SpecializationId.parse("villager_potential:librarian/enchanter");

        assertThrows(
                IllegalStateException.class,
                () -> VillagerPotentialState.createDefault()
                        .withSpecialization(librarian, enchanter)
        );
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
    void migratesVersionFiveWithoutInventingSpecializations() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        ProfessionCareerState career = ProfessionCareerState.firstAssignedAt(100L)
                .withLearnedSkill(0.4);
        ProfessionActivityState activity = new ProfessionActivityState(0.75, 200L);

        VillagerPotentialState migrated = VillagerPotentialState.migrate(
                5,
                Map.of(librarian, 1.25),
                Map.of(librarian, career),
                java.util.Optional.of(librarian),
                Map.of(librarian, activity)
        );

        assertEquals(career, migrated.careerFor(librarian).orElseThrow());
        assertTrue(migrated.specializationFor(librarian).isEmpty());
        assertEquals(activity, migrated.professionActivities().get(librarian));
        assertEquals(VillagerPotentialState.CURRENT_SCHEMA_VERSION, migrated.schemaVersion());
    }

    @Test
    void migratesVersionSixWithoutInventingTradePalettes() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        ProfessionCareerState career = ProfessionCareerState.firstAssignedAt(100L)
                .withLearnedSkill(0.4);
        ProfessionActivityState activity = new ProfessionActivityState(0.75, 200L);

        VillagerPotentialState migrated = VillagerPotentialState.migrate(
                6,
                Map.of(librarian, 1.25),
                Map.of(librarian, career),
                java.util.Optional.of(librarian),
                Map.of(librarian, activity)
        );

        assertTrue(migrated.tradePalettes().isEmpty());
        assertEquals(activity, migrated.professionActivities().get(librarian));
        assertEquals(VillagerPotentialState.CURRENT_SCHEMA_VERSION, migrated.schemaVersion());
    }

    @Test
    void migratesVersionNineDemandWithoutLosingItsTimeAnchor() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        TradeKey paper = trade("minecraft:paper", "minecraft:emerald");
        MarketDemandState demand = new MarketDemandState(7.0, 4L, 300L);

        VillagerPotentialState migrated = VillagerPotentialState.migrate(
                9,
                Map.of(),
                Map.of(),
                java.util.Optional.empty(),
                Map.of(),
                Map.of(),
                Map.of(librarian, Map.of(paper, demand))
        );

        assertEquals(demand, migrated.marketDemandFor(librarian, paper).orElseThrow());
        assertEquals(VillagerPotentialState.CURRENT_SCHEMA_VERSION, migrated.schemaVersion());
    }

    @Test
    void schemaTenCanonicalizationMergesTradeKeyCollisions() {
        ProfessionId cartographer = ProfessionId.parse("minecraft:cartographer");
        TradeKey first = legacyMapTrade("4");
        TradeKey second = legacyMapTrade("91");
        TradePaletteState palette = new TradePaletteState(
                List.of(first, second),
                Map.of(
                        first, new TradeHistory(
                                2L, OptionalLong.of(10L), 3L, OptionalLong.of(20L)),
                        second, new TradeHistory(
                                5L, OptionalLong.of(30L), 7L, OptionalLong.of(15L))
                )
        );

        VillagerPotentialState migrated = VillagerPotentialState.migrate(
                10,
                Map.of(),
                Map.of(),
                Optional.empty(),
                Map.of(),
                Map.of(cartographer, palette),
                Map.of(cartographer, Map.of(
                        first, new MarketDemandState(2.0, 3L, 20L),
                        second, new MarketDemandState(8.0, 5L, 40L)
                ))
        );

        TradePaletteState migratedPalette = migrated.tradePaletteFor(cartographer).orElseThrow();
        assertEquals(1, migratedPalette.activeTrades().size());
        TradeKey canonical = migratedPalette.activeTrades().get(0);
        TradeHistory history = migratedPalette.offerHistory().get(canonical);
        assertEquals(7L, history.timesSeen());
        assertEquals(OptionalLong.of(30L), history.lastSeen());
        assertEquals(10L, history.timesUsed());
        assertEquals(OptionalLong.of(20L), history.lastUsed());

        MarketDemandState demand = migrated.marketDemandFor(cartographer, canonical).orElseThrow();
        assertEquals(8.0, demand.demandScore());
        assertEquals(8L, demand.timesPurchased());
        assertEquals(40L, demand.lastPurchaseGameTime());
    }

    @Test
    void rejectsUnknownNewerSchemaVersion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> VillagerPotentialState.migrate(VillagerPotentialState.CURRENT_SCHEMA_VERSION + 1)
        );
    }

    @Test
    void memoryStrategiesObserveProfessionTimeAndOthersGameTime() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        VillagerPotentialState state = VillagerPotentialState.createDefault()
                .assignProfession(librarian, 100L)
                .withCareer(librarian, new ProfessionCareerState(4_000L, 0.5, 100L, 300L));

        assertEquals(4_000L, state.observationTimeFor(
                librarian, 900_000L, TradePaletteRerollStrategy.WEIGHTED_MEMORY));
        assertEquals(4_000L, state.observationTimeFor(
                librarian, 900_000L, TradePaletteRerollStrategy.EXHAUST));
        assertEquals(4_000L, state.observationTimeFor(
                librarian, 900_000L, TradePaletteRerollStrategy.CYCLIC));
        assertEquals(900_000L, state.observationTimeFor(
                librarian, 900_000L, TradePaletteRerollStrategy.PERSISTENT));
        assertEquals(900_000L, state.observationTimeFor(
                librarian, 900_000L, TradePaletteRerollStrategy.VANILLA));
        // A profession without a career has no accumulated tenure yet.
        assertEquals(0L, state.observationTimeFor(
                ProfessionId.parse("minecraft:farmer"),
                900_000L,
                TradePaletteRerollStrategy.EXHAUST
        ));
    }

    private static TradeKey trade(String cost, String result) {
        return new TradeKey.Offer(
                new TradeKey.Item(cost, 1),
                new TradeKey.Item(result, 1)
        );
    }

    private static TradeKey legacyMapTrade(String mapId) {
        String key = "minecraft:map_id";
        String value = "3:" + mapId;
        String components = key.length() + ":" + key + value.length() + ":" + value;
        return new TradeKey.Offer(
                new TradeKey.Item("minecraft:emerald", 13),
                new TradeKey.Item("minecraft:filled_map", 1, components)
        );
    }
}
