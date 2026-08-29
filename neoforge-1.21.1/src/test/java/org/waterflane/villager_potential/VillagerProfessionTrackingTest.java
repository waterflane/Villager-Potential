package org.waterflane.villager_potential;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.ProfessionCareerState;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.SpecializationId;
import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VillagerProfessionTrackingTest {
    private static final ProfessionId LIBRARIAN = ProfessionId.parse("minecraft:librarian");
    private static final ProfessionId FARMER = ProfessionId.parse("minecraft:farmer");

    @Test
    void professionSpecializationAndBatchedSkillLifecycleEventsFire() {
        CareerCarrier carrier = carrier(VillagerProfession.NONE, baseState(), 100L);
        AtomicInteger professionEvents = new AtomicInteger();
        AtomicInteger specializationEvents = new AtomicInteger();
        AtomicInteger skillEvents = new AtomicInteger();

        try (var profession = VillagerPotentialLifecycleEvents.onProfessionChanged(event ->
                professionEvents.incrementAndGet()
        ); var specialization = VillagerPotentialLifecycleEvents.onSpecializationAssigned(event ->
                specializationEvents.incrementAndGet()
        ); var skill = VillagerPotentialLifecycleEvents.onSkillChanged(event ->
                skillEvents.incrementAndGet()
        )) {
            carrier.profession().set(VillagerProfession.LIBRARIAN);
            tick(carrier);
            tick(carrier, 20);
        }

        assertEquals(1, professionEvents.get());
        assertEquals(1, specializationEvents.get());
        assertEquals(1, skillEvents.get());
    }

    @Test
    void unemployedToLibrarianCreatesCareerWithoutChangingAptitude() {
        CareerCarrier carrier = carrier(VillagerProfession.NONE, baseState(), 100L);

        carrier.profession().set(VillagerProfession.LIBRARIAN);
        tick(carrier);

        VillagerPotentialState state = carrier.state().get();
        assertEquals(LIBRARIAN, state.activeProfession().orElseThrow());
        assertEquals(
                ProfessionCareerState.firstAssignedAt(100L)
                        .withSpecialization(SpecializationId.GENERAL),
                state.careerFor(LIBRARIAN).orElseThrow()
        );
        assertEquals(1.25, state.aptitudeFor(LIBRARIAN).orElseThrow());
        verify(carrier.villager(), times(1)).setData(any(Supplier.class), any());
        verify(carrier.villager(), never()).setVillagerData(any());
    }

    @Test
    void existingLeveledVillagerBootstrapsCareerSkillWithoutDemotion() {
        CareerCarrier carrier = carrier(
                VillagerProfession.LIBRARIAN,
                baseState(),
                100L,
                4
        );

        tick(carrier);

        ProfessionCareerState career = carrier.state().get()
                .careerFor(LIBRARIAN).orElseThrow();
        assertEquals(
                ServerConfig.gameplayConfig().skill()
                        .professionLevelThresholds().thresholdForLevel(4),
                career.learnedSkill()
        );
        assertEquals(4, carrier.villager().getVillagerData().getLevel());
        verify(carrier.villager(), never()).setVillagerData(any());
    }

    @Test
    void initialMigrationKeepsUsedVanillaOffersAndInventsNoTradeMemory() {
        CareerCarrier carrier = carrier(
                VillagerProfession.LIBRARIAN,
                baseState(),
                100L,
                3
        );
        MerchantOffer usedOffer = new MerchantOffer(
                new ItemCost(Items.PAPER, 24),
                Optional.empty(),
                new ItemStack(Items.EMERALD),
                12,
                2,
                0.05F
        );
        usedOffer.increaseUses();
        MerchantOffers existingOffers = new MerchantOffers();
        existingOffers.add(usedOffer);
        when(carrier.villager().getOffers()).thenReturn(existingOffers);

        tick(carrier);

        assertEquals(1, existingOffers.size());
        assertSame(usedOffer, existingOffers.getFirst());
        assertEquals(1, usedOffer.getUses());
        assertTrue(carrier.state().get().tradePalettes().isEmpty());
        verify(carrier.villager(), never()).setVillagerData(any());
    }

    @Test
    void librarianToUnemployedClearsOnlyTheActiveProfession() {
        ProfessionCareerState librarianCareer = new ProfessionCareerState(80L, 0.75, 20L, 20L)
                .withSpecialization(SpecializationId.GENERAL);
        CareerCarrier carrier = carrier(
                VillagerProfession.LIBRARIAN,
                baseState().assignProfession(LIBRARIAN, 20L).withCareer(LIBRARIAN, librarianCareer),
                120L
        );

        carrier.profession().set(VillagerProfession.NONE);
        tick(carrier);

        VillagerPotentialState state = carrier.state().get();
        assertTrue(state.activeProfession().isEmpty());
        assertEquals(librarianCareer, state.careerFor(LIBRARIAN).orElseThrow());
        assertEquals(1.25, state.aptitudeFor(LIBRARIAN).orElseThrow());
    }

    @Test
    void librarianToFarmerCreatesSecondCareerWithoutGrowingOldSkill() {
        ProfessionCareerState librarianCareer = new ProfessionCareerState(80L, 0.75, 20L, 20L)
                .withSpecialization(SpecializationId.GENERAL);
        CareerCarrier carrier = carrier(
                VillagerProfession.LIBRARIAN,
                baseState().assignProfession(LIBRARIAN, 20L).withCareer(LIBRARIAN, librarianCareer),
                140L
        );

        carrier.profession().set(VillagerProfession.FARMER);
        tick(carrier);
        VillagerPotentialState changedState = carrier.state().get();
        tick(carrier);

        VillagerPotentialState secondTickState = carrier.state().get();
        assertEquals(FARMER, secondTickState.activeProfession().orElseThrow());
        assertEquals(
                ProfessionCareerState.firstAssignedAt(140L)
                        .withSpecialization(SpecializationId.GENERAL),
                secondTickState.careerFor(FARMER).orElseThrow()
        );
        assertEquals(librarianCareer, changedState.careerFor(LIBRARIAN).orElseThrow());
        verify(carrier.villager(), times(1)).setData(any(Supplier.class), any());
    }

    @Test
    void migrationBootstrapDoesNotGrantSkillToALaterSecondCareer() {
        ProfessionCareerState librarianCareer = new ProfessionCareerState(80L, 0.75, 20L, 20L)
                .withSpecialization(SpecializationId.GENERAL);
        CareerCarrier carrier = carrier(
                VillagerProfession.LIBRARIAN,
                baseState().assignProfession(LIBRARIAN, 20L)
                        .withCareer(LIBRARIAN, librarianCareer),
                140L,
                4
        );

        carrier.profession().set(VillagerProfession.FARMER);
        tick(carrier);

        assertEquals(0.0, carrier.state().get().careerFor(FARMER)
                .orElseThrow().learnedSkill());
        assertEquals(librarianCareer, carrier.state().get().careerFor(LIBRARIAN).orElseThrow());
    }

    @Test
    void farmerToLibrarianRestoresExistingCareerHistory() {
        ProfessionCareerState librarianCareer = new ProfessionCareerState(80L, 0.75, 20L, 20L)
                .withSpecialization(SpecializationId.GENERAL);
        VillagerPotentialState original = baseState()
                .assignProfession(LIBRARIAN, 20L)
                .withCareer(LIBRARIAN, librarianCareer)
                .assignProfession(FARMER, 140L);
        CareerCarrier carrier = carrier(VillagerProfession.FARMER, original, 200L);

        carrier.profession().set(VillagerProfession.LIBRARIAN);
        tick(carrier);

        VillagerPotentialState state = carrier.state().get();
        assertEquals(LIBRARIAN, state.activeProfession().orElseThrow());
        assertEquals(
                new ProfessionCareerState(80L, 0.75, 20L, 200L)
                        .withSpecialization(SpecializationId.GENERAL),
                state.careerFor(LIBRARIAN).orElseThrow()
        );
        assertTrue(state.careerFor(FARMER).isPresent());
        assertEquals(original.aptitudes(), state.aptitudes());
    }

    @Test
    void activeLibrarianSkillIncreasesInBatchedIntervals() {
        CareerCarrier carrier = carrier(
                VillagerProfession.LIBRARIAN,
                baseState().assignProfession(LIBRARIAN, 20L),
                100L
        );

        tick(carrier, 19);

        ProfessionCareerState beforeInterval = carrier.state().get()
                .careerFor(LIBRARIAN).orElseThrow();
        assertEquals(0L, beforeInterval.accumulatedProfessionTime());
        assertEquals(0.0, beforeInterval.learnedSkill());
        verify(carrier.villager(), never()).setData(any(Supplier.class), any());

        tick(carrier);

        ProfessionCareerState progressed = carrier.state().get()
                .careerFor(LIBRARIAN).orElseThrow();
        assertEquals(20L, progressed.accumulatedProfessionTime());
        assertEquals(expectedSkill(20, 1.25), progressed.learnedSkill(), 0.000_000_1);
        verify(carrier.villager(), times(1)).setData(any(Supplier.class), any());
        verify(carrier.villager(), never()).setVillagerData(any());
    }

    @Test
    void chunkUnloadFlushesPartialProgressAndReloadResumesIt() {
        CareerCarrier beforeUnload = carrier(
                VillagerProfession.LIBRARIAN,
                baseState().assignProfession(LIBRARIAN, 20L),
                100L
        );
        tick(beforeUnload, 7);
        assertEquals(0L, beforeUnload.state().get().careerFor(LIBRARIAN)
                .orElseThrow().accumulatedProfessionTime());

        VillagerPotentialEvents.onEntityLeaveLevel(new EntityLeaveLevelEvent(
                beforeUnload.villager(),
                beforeUnload.villager().level()
        ));
        VillagerPotentialState saved = beforeUnload.state().get();
        assertEquals(7L, saved.careerFor(LIBRARIAN)
                .orElseThrow().accumulatedProfessionTime());

        CareerCarrier afterReload = carrier(
                VillagerProfession.LIBRARIAN,
                saved,
                200L
        );
        tick(afterReload, 13);
        VillagerPotentialAttachments.flushProfessionProgress(afterReload.villager());

        ProfessionCareerState resumed = afterReload.state().get()
                .careerFor(LIBRARIAN).orElseThrow();
        assertEquals(20L, resumed.accumulatedProfessionTime());
        assertEquals(expectedSkill(20, 1.25), resumed.learnedSkill(), 0.000_000_1);
    }

    @Test
    void recentTradingAcceleratesSubsequentTimeBasedProgression() {
        CareerCarrier inactive = carrier(
                VillagerProfession.LIBRARIAN,
                baseState().assignProfession(LIBRARIAN, 20L),
                100L
        );
        CareerCarrier active = carrier(
                VillagerProfession.LIBRARIAN,
                baseState().assignProfession(LIBRARIAN, 20L),
                100L
        );
        VillagerPotentialAttachments.recordTrade(active.villager(), 100L);

        tick(inactive, 20);
        tick(active, 20);

        double inactiveSkill = inactive.state().get().careerFor(LIBRARIAN)
                .orElseThrow().learnedSkill();
        double activeSkill = active.state().get().careerFor(LIBRARIAN)
                .orElseThrow().learnedSkill();
        assertEquals(expectedSkill(20, 1.25), inactiveSkill, 0.000_000_1);
        assertEquals(expectedSkill(20, 1.25, 1.1), activeSkill, 0.000_000_1);
        assertTrue(activeSkill > inactiveSkill);
    }

    @Test
    void burstTradesCannotExceedTheActivityMultiplierCap() {
        CareerCarrier active = carrier(
                VillagerProfession.LIBRARIAN,
                baseState().assignProfession(LIBRARIAN, 20L),
                100L
        );
        for (int trade = 0; trade < 100; trade++) {
            VillagerPotentialAttachments.recordTrade(active.villager(), 100L);
        }

        assertEquals(
                0.0,
                active.state().get().careerFor(LIBRARIAN).orElseThrow().learnedSkill()
        );
        tick(active, 20);

        assertEquals(
                expectedSkill(20, 1.25, 2.0),
                active.state().get().careerFor(LIBRARIAN).orElseThrow().learnedSkill(),
                0.000_000_1
        );
    }

    @Test
    void unemployedVillagerDoesNotGainSkill() {
        CareerCarrier unemployed = carrier(VillagerProfession.NONE, baseState(), 100L);
        tick(unemployed, 40);

        assertTrue(unemployed.state().get().careers().isEmpty());
        verify(unemployed.villager(), never()).setData(any(Supplier.class), any());
    }

    @Test
    void higherAptitudeProgressesFasterForEqualProfessionTime() {
        CareerCarrier ordinary = carrier(
                VillagerProfession.LIBRARIAN,
                stateWithAptitudes(0.75, 0.8).assignProfession(LIBRARIAN, 20L),
                100L
        );
        CareerCarrier talented = carrier(
                VillagerProfession.LIBRARIAN,
                stateWithAptitudes(1.5, 0.8).assignProfession(LIBRARIAN, 20L),
                100L
        );

        tick(ordinary, 20);
        tick(talented, 20);

        double ordinarySkill = ordinary.state().get().careerFor(LIBRARIAN)
                .orElseThrow().learnedSkill();
        double talentedSkill = talented.state().get().careerFor(LIBRARIAN)
                .orElseThrow().learnedSkill();
        assertEquals(expectedSkill(20, 0.75), ordinarySkill, 0.000_000_1);
        assertEquals(expectedSkill(20, 1.5), talentedSkill, 0.000_000_1);
        assertTrue(talentedSkill > ordinarySkill);
    }

    @Test
    void switchingProfessionStopsOldSkillGrowth() {
        CareerCarrier carrier = carrier(
                VillagerProfession.LIBRARIAN,
                baseState().assignProfession(LIBRARIAN, 20L),
                100L
        );
        tick(carrier, 10);
        double librarianSkillAtSwitch = carrier.state().get().careerFor(LIBRARIAN)
                .orElseThrow().learnedSkill();
        assertEquals(0.0, librarianSkillAtSwitch);

        carrier.profession().set(VillagerProfession.FARMER);
        tick(carrier, 20);

        VillagerPotentialState state = carrier.state().get();
        assertEquals(
                expectedSkill(10, 1.25),
                state.careerFor(LIBRARIAN).orElseThrow().learnedSkill(),
                0.000_000_1
        );
        assertEquals(
                expectedSkill(20, 0.8),
                state.careerFor(FARMER).orElseThrow().learnedSkill(),
                0.000_000_1
        );
    }

    @Test
    void babyVillagerDoesNotAccumulateTenureOrSkill() {

        CareerCarrier baby = carrier(
                VillagerProfession.LIBRARIAN,
                baseState().assignProfession(LIBRARIAN, 20L),
                100L
        );
        when(baby.villager().isBaby()).thenReturn(true);
        tick(baby, 40);

        assertEquals(
                0L,
                baby.state().get().careerFor(LIBRARIAN).orElseThrow()
                        .accumulatedProfessionTime()
        );
        assertEquals(
                0.0,
                baby.state().get().careerFor(LIBRARIAN).orElseThrow().learnedSkill()
        );
        verify(baby.villager(), never()).setData(any(Supplier.class), any());
    }

    @Test
    void nightTicksDoNotAccumulateTenureOrSkill() {
        CareerCarrier villager = carrier(
                VillagerProfession.LIBRARIAN,
                baseState().assignProfession(LIBRARIAN, 20L),
                13_000L
        );
        when(villager.villager().level().getDayTime()).thenReturn(13_000L);

        tick(villager, 40);

        assertEquals(
                0L,
                villager.state().get().careerFor(LIBRARIAN).orElseThrow()
                        .accumulatedProfessionTime()
        );
        assertEquals(
                0.0,
                villager.state().get().careerFor(LIBRARIAN).orElseThrow().learnedSkill()
        );
    }

    @Test
    void clientTicksNeverAccessProfessionState() {
        Villager villager = mock(Villager.class);
        when(villager.level()).thenReturn(mock(Level.class));

        VillagerPotentialEvents.onEntityTickPost(new EntityTickEvent.Post(villager));

        verify(villager, never()).getData(any(Supplier.class));
        verify(villager, never()).setData(any(Supplier.class), any());
    }

    private static VillagerPotentialState baseState() {
        return stateWithAptitudes(1.25, 0.8);
    }

    private static double expectedSkill(long ticks, double aptitude) {
        return expectedSkill(ticks, aptitude, 1.0);
    }

    private static double expectedSkill(long ticks, double aptitude, double activity) {
        return ticks
                * VillagerPotentialAttachments.SKILL_PROGRESSION_CONFIG.progressionRate()
                * aptitude
                * activity;
    }

    private static VillagerPotentialState stateWithAptitudes(
            double librarianAptitude,
            double farmerAptitude
    ) {
        return new VillagerPotentialState(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                Map.of(LIBRARIAN, librarianAptitude, FARMER, farmerAptitude)
        );
    }

    private static void tick(CareerCarrier carrier) {
        VillagerPotentialEvents.onEntityTickPost(new EntityTickEvent.Post(carrier.villager()));
    }

    private static void tick(CareerCarrier carrier, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            tick(carrier);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static CareerCarrier carrier(
            VillagerProfession initialProfession,
            VillagerPotentialState initialState,
            long gameTime
    ) {
        return carrier(initialProfession, initialState, gameTime, 1);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static CareerCarrier carrier(
            VillagerProfession initialProfession,
            VillagerPotentialState initialState,
            long gameTime,
            int vanillaLevel
    ) {
        Villager villager = mock(Villager.class);
        VillagerData villagerData = mock(VillagerData.class);
        ServerLevel level = mock(ServerLevel.class);
        AtomicReference<VillagerProfession> profession = new AtomicReference<>(initialProfession);
        VillagerPotentialState persistedState = initialState;
        for (Map.Entry<ProfessionId, ProfessionCareerState> career
                : initialState.careers().entrySet()) {
            if (career.getValue().specialization().isEmpty()) {
                persistedState = persistedState.withSpecialization(
                        career.getKey(),
                        SpecializationId.GENERAL
                );
            }
        }
        AtomicReference<VillagerPotentialState> state = new AtomicReference<>(persistedState);

        when(level.getGameTime()).thenReturn(gameTime);
        when(level.getDayTime()).thenReturn(gameTime);
        when(villager.level()).thenReturn(level);
        when(villager.getUUID()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        when(villager.getVillagerData()).thenReturn(villagerData);
        when(villagerData.getProfession()).thenAnswer(ignored -> profession.get());
        when(villagerData.getLevel()).thenReturn(vanillaLevel);
        when(villager.getData(any(Supplier.class))).thenAnswer(ignored -> state.get());
        when(villager.setData(any(Supplier.class), any())).thenAnswer(invocation ->
                state.getAndSet(invocation.getArgument(1))
        );
        return new CareerCarrier(villager, profession, state);
    }

    private record CareerCarrier(
            Villager villager,
            AtomicReference<VillagerProfession> profession,
            AtomicReference<VillagerPotentialState> state
    ) {
    }
}
