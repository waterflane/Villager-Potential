package org.waterflane.villager_potential;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.ProfessionCareerState;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void unemployedToLibrarianCreatesCareerWithoutChangingAptitude() {
        CareerCarrier carrier = carrier(VillagerProfession.NONE, baseState(), 100L);

        carrier.profession().set(VillagerProfession.LIBRARIAN);
        tick(carrier);

        VillagerPotentialState state = carrier.state().get();
        assertEquals(LIBRARIAN, state.activeProfession().orElseThrow());
        assertEquals(
                ProfessionCareerState.firstAssignedAt(100L).accumulateProfessionTime(1L),
                state.careerFor(LIBRARIAN).orElseThrow()
        );
        assertEquals(1.25, state.aptitudeFor(LIBRARIAN).orElseThrow());
        verify(carrier.villager(), times(1)).setData(any(Supplier.class), any());
        verify(carrier.villager(), never()).setVillagerData(any());
    }

    @Test
    void librarianToUnemployedClearsOnlyTheActiveProfession() {
        ProfessionCareerState librarianCareer = new ProfessionCareerState(80L, 0.75, 20L, 20L);
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
    void librarianToFarmerCreatesSecondCareerAndIgnoresDuplicateTicks() {
        ProfessionCareerState librarianCareer = new ProfessionCareerState(80L, 0.75, 20L, 20L);
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
                ProfessionCareerState.firstAssignedAt(140L).accumulateProfessionTime(2L),
                secondTickState.careerFor(FARMER).orElseThrow()
        );
        assertEquals(librarianCareer, changedState.careerFor(LIBRARIAN).orElseThrow());
        verify(carrier.villager(), times(2)).setData(any(Supplier.class), any());
    }

    @Test
    void farmerToLibrarianRestoresExistingCareerHistory() {
        ProfessionCareerState librarianCareer = new ProfessionCareerState(80L, 0.75, 20L, 20L);
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
                new ProfessionCareerState(81L, 0.75, 20L, 200L),
                state.careerFor(LIBRARIAN).orElseThrow()
        );
        assertTrue(state.careerFor(FARMER).isPresent());
        assertEquals(original.aptitudes(), state.aptitudes());
    }

    @Test
    void activeAdultProfessionAccumulatesOneLoadedServerTickAtATime() {
        CareerCarrier carrier = carrier(
                VillagerProfession.LIBRARIAN,
                baseState().assignProfession(LIBRARIAN, 20L),
                100L
        );

        tick(carrier);
        tick(carrier);

        assertEquals(
                2L,
                carrier.state().get().careerFor(LIBRARIAN).orElseThrow()
                        .accumulatedProfessionTime()
        );
    }

    @Test
    void unemployedAndBabyVillagersDoNotAccumulateTenure() {
        CareerCarrier unemployed = carrier(VillagerProfession.NONE, baseState(), 100L);
        tick(unemployed);

        CareerCarrier baby = carrier(
                VillagerProfession.LIBRARIAN,
                baseState().assignProfession(LIBRARIAN, 20L),
                100L
        );
        when(baby.villager().isBaby()).thenReturn(true);
        tick(baby);

        assertTrue(unemployed.state().get().careers().isEmpty());
        assertEquals(
                0L,
                baby.state().get().careerFor(LIBRARIAN).orElseThrow()
                        .accumulatedProfessionTime()
        );
        verify(unemployed.villager(), never()).setData(any(Supplier.class), any());
        verify(baby.villager(), never()).setData(any(Supplier.class), any());
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
        return new VillagerPotentialState(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                Map.of(LIBRARIAN, 1.25, FARMER, 0.8)
        );
    }

    private static void tick(CareerCarrier carrier) {
        VillagerPotentialEvents.onEntityTickPost(new EntityTickEvent.Post(carrier.villager()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static CareerCarrier carrier(
            VillagerProfession initialProfession,
            VillagerPotentialState initialState,
            long gameTime
    ) {
        Villager villager = mock(Villager.class);
        VillagerData villagerData = mock(VillagerData.class);
        ServerLevel level = mock(ServerLevel.class);
        AtomicReference<VillagerProfession> profession = new AtomicReference<>(initialProfession);
        AtomicReference<VillagerPotentialState> state = new AtomicReference<>(initialState);

        when(level.getGameTime()).thenReturn(gameTime);
        when(villager.level()).thenReturn(level);
        when(villager.getUUID()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        when(villager.getVillagerData()).thenReturn(villagerData);
        when(villagerData.getProfession()).thenAnswer(ignored -> profession.get());
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
