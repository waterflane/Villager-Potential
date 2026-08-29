package org.waterflane.villager_potential;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class VillagerLevelProgressionTest {
    private static final ProfessionId LIBRARIAN =
            ProfessionId.parse("minecraft:librarian");

    @Test
    void appliedVanillaLevelChangeFiresLifecycleEvent() {
        ProgressionCarrier carrier = carrier();
        AtomicReference<VillagerPotentialLifecycleEvents.VanillaLevelChanged> observed =
                new AtomicReference<>();
        VillagerPotentialAttachments.trackProfession(carrier.villager(), 100L);

        try (var registration = VillagerPotentialLifecycleEvents.onVanillaLevelChanged(
                observed::set
        )) {
            carrier.vanillaLevel().set(2);
            VillagerPotentialAttachments.trackProfession(carrier.villager(), 101L);
        }

        assertEquals(1, observed.get().previousLevel());
        assertEquals(2, observed.get().level());
        assertEquals(LIBRARIAN, observed.get().profession());
    }

    @Test
    void timeProgressionQueuesTheEarnedLevel() {
        ProgressionCarrier carrier = carrier();

        tick(carrier, 4_000);

        assertEquals(
                0.2,
                carrier.state().get().careerFor(LIBRARIAN)
                        .orElseThrow().learnedSkill(),
                0.000_000_1
        );
        verify(carrier.levelUpAccess(), times(1)).villagerPotential$queueLevelUp();
    }

    @Test
    void tradesRecordActivityButCannotQueueAnImmediateLevel() {
        ProgressionCarrier carrier = carrier();

        for (int trade = 0; trade < 100; trade++) {
            VillagerPotentialAttachments.recordTrade(carrier.villager(), 100L);
        }

        assertEquals(
                0.0,
                carrier.state().get().careerFor(LIBRARIAN)
                        .orElseThrow().learnedSkill()
        );
        verify(carrier.levelUpAccess(), never()).villagerPotential$queueLevelUp();
    }

    @Test
    void earnedLevelIsQueuedInsteadOfAppliedOrSkippedDirectly() {
        ProgressionCarrier carrier = carrier();
        VillagerPotentialState masterSkill = carrier.state().get()
                .progressActiveProfession(
                        100_000L,
                        VillagerPotentialAttachments.SKILL_PROGRESSION_CONFIG
                );

        VillagerPotentialAttachments.queueEarnedProfessionLevel(
                carrier.villager(),
                masterSkill,
                LIBRARIAN
        );

        verify(carrier.levelUpAccess(), times(1)).villagerPotential$queueLevelUp();
        verify(carrier.villager(), never()).setVillagerData(any());
    }

    private static void tick(ProgressionCarrier carrier, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            VillagerPotentialAttachments.trackProfession(carrier.villager(), 100L + tick);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ProgressionCarrier carrier() {
        Villager villager = mock(
                Villager.class,
                withSettings().extraInterfaces(VillagerLevelUpAccess.class)
        );
        VillagerLevelUpAccess levelUpAccess = (VillagerLevelUpAccess) villager;
        VillagerData villagerData = mock(VillagerData.class);
        ServerLevel level = mock(ServerLevel.class);
        VillagerPotentialState initialState = new VillagerPotentialState(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                Map.of(LIBRARIAN, 1.0)
        ).assignProfession(LIBRARIAN, 0L);
        AtomicReference<VillagerPotentialState> state = new AtomicReference<>(initialState);
        AtomicInteger vanillaLevel = new AtomicInteger(1);

        when(levelUpAccess.villagerPotential$queueLevelUp()).thenReturn(true);
        when(villager.level()).thenReturn(level);
        when(villager.getUUID()).thenReturn(
                UUID.fromString("00000000-0000-0000-0000-000000000020")
        );
        when(villager.getVillagerData()).thenReturn(villagerData);
        when(villagerData.getProfession()).thenReturn(VillagerProfession.LIBRARIAN);
        when(villagerData.getLevel()).thenAnswer(ignored -> vanillaLevel.get());
        when(villager.getData(any(Supplier.class))).thenAnswer(ignored -> state.get());
        when(villager.setData(any(Supplier.class), any())).thenAnswer(invocation ->
                state.getAndSet(invocation.getArgument(1))
        );
        return new ProgressionCarrier(villager, levelUpAccess, state, vanillaLevel);
    }

    private record ProgressionCarrier(
            Villager villager,
            VillagerLevelUpAccess levelUpAccess,
            AtomicReference<VillagerPotentialState> state,
            AtomicInteger vanillaLevel
    ) {
    }
}
