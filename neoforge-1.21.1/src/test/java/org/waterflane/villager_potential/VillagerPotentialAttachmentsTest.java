package org.waterflane.villager_potential;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.UUID;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VillagerPotentialAttachmentsTest {
    private static final long WORLD_SEED = 8_675_309L;
    private static final UUID FIRST_VILLAGER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_VILLAGER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void newVillagerGetsEverySupportedVanillaAptitude() {
        Villager villager = villagerWith(null, FIRST_VILLAGER_ID, WORLD_SEED);

        VillagerPotentialState state = VillagerPotentialAttachments.get(villager);

        assertEquals(13, VillagerProfessionIds.supportedVanillaProfessions().size());
        assertEquals(
                VillagerProfessionIds.supportedVanillaProfessions().size(),
                state.aptitudes().size()
        );
        assertEquals(
                Set.copyOf(VillagerProfessionIds.supportedVanillaProfessions()),
                state.aptitudes().keySet()
        );
        assertTrue(VillagerProfessionIds.supportedVanillaProfessions().stream()
                .allMatch(profession -> state.aptitudeFor(profession).isPresent()));
    }

    @Test
    void newServerSideVillagerIsInitializedWhenItJoinsTheLevel() {
        Villager villager = villagerWith(null, FIRST_VILLAGER_ID, WORLD_SEED);
        ServerLevel level = (ServerLevel) villager.level();

        VillagerPotentialEvents.onEntityJoinLevel(
                new EntityJoinLevelEvent(villager, level, false)
        );
        VillagerPotentialState state = VillagerPotentialAttachments.get(villager);

        assertEquals(13, state.aptitudes().size());
        verify(villager, times(1)).setData(any(Supplier.class), eq(state));
    }

    @Test
    void diskLoadedVillagerRemainsLazyUntilFirstAccess() {
        Villager villager = villagerWith(null, FIRST_VILLAGER_ID, WORLD_SEED);
        ServerLevel level = (ServerLevel) villager.level();

        VillagerPotentialEvents.onEntityJoinLevel(
                new EntityJoinLevelEvent(villager, level, true)
        );

        verify(villager, times(0)).getData(any(Supplier.class));
        assertEquals(13, VillagerPotentialAttachments.get(villager).aptitudes().size());
        verify(villager, times(1)).setData(any(Supplier.class), any());
    }

    @Test
    void repeatedAccessPreservesGeneratedValues() {
        Villager villager = villagerWith(null, FIRST_VILLAGER_ID, WORLD_SEED);

        VillagerPotentialState firstAccess = VillagerPotentialAttachments.get(villager);
        VillagerPotentialState secondAccess = VillagerPotentialAttachments.get(villager);

        assertSame(firstAccess, secondAccess);
        verify(villager, times(1)).setData(any(Supplier.class), eq(firstAccess));
    }

    @Test
    void generatedAptitudesSurviveSaveAndLoad() {
        VillagerPotentialState original = VillagerPotentialAttachments.get(
                villagerWith(null, FIRST_VILLAGER_ID, WORLD_SEED)
        );

        Tag serialized = VillagerPotentialAttachments.CODEC
                .encodeStart(NbtOps.INSTANCE, original)
                .getOrThrow();
        VillagerPotentialState restored = VillagerPotentialAttachments.CODEC
                .parse(NbtOps.INSTANCE, serialized)
                .getOrThrow();
        Villager loadedVillager = villagerWith(restored, FIRST_VILLAGER_ID, WORLD_SEED);

        assertEquals(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                ((CompoundTag) serialized).getInt("schema_version")
        );
        assertEquals(original, restored);
        assertSame(restored, VillagerPotentialAttachments.get(loadedVillager));
        verify(loadedVillager, times(0)).setData(any(Supplier.class), any());
    }

    @Test
    void twoVillagersCanHaveDifferentAptitudes() {
        VillagerPotentialState first = VillagerPotentialAttachments.get(
                villagerWith(null, FIRST_VILLAGER_ID, WORLD_SEED)
        );
        VillagerPotentialState second = VillagerPotentialAttachments.get(
                villagerWith(null, SECOND_VILLAGER_ID, WORLD_SEED)
        );

        assertNotEquals(first.aptitudes(), second.aptitudes());
    }

    @Test
    void oldVillagerUsesTheSameDeterministicInitializationPath() {
        Villager oldVillager = villagerWith(null, FIRST_VILLAGER_ID, WORLD_SEED);

        VillagerPotentialState lazyState = VillagerPotentialAttachments.get(oldVillager);

        assertEquals(
                VillagerPotentialAttachments.initialize(WORLD_SEED, FIRST_VILLAGER_ID),
                lazyState
        );
    }

    @Test
    void accidentalRepeatedInitializationCannotRerollAptitudes() {
        assertEquals(
                VillagerPotentialAttachments.initialize(WORLD_SEED, FIRST_VILLAGER_ID),
                VillagerPotentialAttachments.initialize(WORLD_SEED, FIRST_VILLAGER_ID)
        );
    }

    @Test
    void olderSchemaStillUsesTheLazyInitializationSentinel() {
        CompoundTag serialized = new CompoundTag();
        serialized.putInt("schema_version", 1);

        VillagerPotentialState restored = VillagerPotentialAttachments.CODEC
                .parse(NbtOps.INSTANCE, serialized)
                .getOrThrow();

        assertEquals(VillagerPotentialState.createDefault(), restored);
        assertTrue(restored.aptitudes().isEmpty());
    }

    @Test
    void syntheticVersionZeroStillUsesTheLazyInitializationSentinel() {
        CompoundTag serialized = new CompoundTag();
        serialized.putInt("schema_version", 0);

        VillagerPotentialState restored = VillagerPotentialAttachments.CODEC
                .parse(NbtOps.INSTANCE, serialized)
                .getOrThrow();

        assertEquals(VillagerPotentialState.createDefault(), restored);
    }

    @Test
    void newerSchemaFailsWithoutReinterpretation() {
        CompoundTag serialized = new CompoundTag();
        serialized.putInt("schema_version", VillagerPotentialState.CURRENT_SCHEMA_VERSION + 1);

        assertTrue(VillagerPotentialAttachments.CODEC.parse(NbtOps.INSTANCE, serialized).isError());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Villager villagerWith(
            VillagerPotentialState state,
            UUID villagerId,
            long worldSeed
    ) {
        Villager villager = mock(Villager.class);
        ServerLevel level = mock(ServerLevel.class);
        AtomicReference<VillagerPotentialState> storedState = new AtomicReference<>(state);

        when(level.getSeed()).thenReturn(worldSeed);
        when(villager.level()).thenReturn(level);
        when(villager.getUUID()).thenReturn(villagerId);
        when(villager.getData(any(Supplier.class))).thenAnswer(ignored -> {
            VillagerPotentialState existingState = storedState.get();
            if (existingState != null) {
                return existingState;
            }

            VillagerPotentialState defaultState = VillagerPotentialState.createDefault();
            storedState.set(defaultState);
            return defaultState;
        });
        when(villager.setData(any(Supplier.class), any())).thenAnswer(invocation -> {
            VillagerPotentialState replacement = invocation.getArgument(1);
            return storedState.getAndSet(replacement);
        });
        return villager;
    }
}
