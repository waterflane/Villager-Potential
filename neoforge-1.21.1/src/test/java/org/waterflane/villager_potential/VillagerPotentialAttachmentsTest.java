package org.waterflane.villager_potential;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.AptitudeInheritance;
import org.waterflane.villager_potential.core.ProfessionCareerState;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
    void newServerSideVillagerIsInitializedOnItsFirstTickAfterJoining() {
        Villager villager = villagerWith(null, FIRST_VILLAGER_ID, WORLD_SEED);
        ServerLevel level = (ServerLevel) villager.level();

        VillagerPotentialEvents.onEntityJoinLevel(
                new EntityJoinLevelEvent(villager, level, false)
        );
        verify(villager, times(0)).getData(any(Supplier.class));

        VillagerPotentialEvents.onEntityTickPre(new EntityTickEvent.Pre(villager));
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
    void professionCareersSurviveSerializationRoundTrip() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        ProfessionId farmer = ProfessionId.parse("minecraft:farmer");
        VillagerPotentialState original = VillagerPotentialState.createDefault()
                .withAptitude(librarian, 1.25)
                .assignProfession(librarian, 100L)
                .withCareer(
                        librarian,
                        new ProfessionCareerState(12_345L, 0.75, 100L, 100L)
                )
                .assignProfession(farmer, 200L)
                .assignProfession(librarian, 300L);

        Tag serialized = VillagerPotentialAttachments.CODEC
                .encodeStart(NbtOps.INSTANCE, original)
                .getOrThrow();
        VillagerPotentialState restored = VillagerPotentialAttachments.CODEC
                .parse(NbtOps.INSTANCE, serialized)
                .getOrThrow();

        assertEquals(original, restored);
        assertEquals(2, restored.careers().size());
        assertEquals(librarian, restored.activeProfession().orElseThrow());
        assertEquals(
                12_345L,
                restored.careerFor(librarian).orElseThrow().accumulatedProfessionTime()
        );
        assertEquals(0.75, restored.careerFor(librarian).orElseThrow().learnedSkill());
        assertEquals(1.25, restored.aptitudeFor(librarian).orElseThrow());
    }

    @Test
    void tradeActivityPersistsOnlyScoreAndDecayAnchor() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        VillagerPotentialState original = new VillagerPotentialState(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                Map.of(librarian, 1.25)
        ).recordProfessionTrade(
                librarian,
                500L,
                VillagerPotentialAttachments.PROFESSION_ACTIVITY_CONFIG
        );

        Tag serialized = VillagerPotentialAttachments.CODEC
                .encodeStart(NbtOps.INSTANCE, original)
                .getOrThrow();
        VillagerPotentialState restored = VillagerPotentialAttachments.CODEC
                .parse(NbtOps.INSTANCE, serialized)
                .getOrThrow();
        CompoundTag activity = ((CompoundTag) serialized)
                .getCompound("profession_activity")
                .getCompound(librarian.toString());

        assertEquals(Set.of("score", "last_update_game_time"), activity.getAllKeys());
        assertEquals(original, restored);
        assertEquals(
                VillagerPotentialAttachments.PROFESSION_ACTIVITY_CONFIG.increasePerTrade(),
                restored.professionActivityFor(
                        librarian,
                        500L,
                        VillagerPotentialAttachments.PROFESSION_ACTIVITY_CONFIG
                )
        );
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
    void childInheritsFromTwoInitializedParentsWithoutChangingThem() {
        VillagerPotentialState firstParentState = stateWithEveryAptitude(1.4);
        VillagerPotentialState secondParentState = stateWithEveryAptitude(0.8);
        Villager firstParent = villagerWith(firstParentState, FIRST_VILLAGER_ID, WORLD_SEED);
        Villager secondParent = villagerWith(secondParentState, SECOND_VILLAGER_ID, WORLD_SEED);
        Villager child = villagerWith(
                null,
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                WORLD_SEED
        );
        Random expectedRandom = new Random(123L);
        VillagerPotentialState expected = AptitudeInheritance.inherit(
                firstParentState,
                secondParentState,
                VillagerProfessionIds.supportedVanillaProfessions(),
                VillagerPotentialAttachments.APTITUDE_CONFIG,
                VillagerPotentialAttachments.INHERITANCE_CONFIG,
                expectedRandom
        );

        VillagerPotentialState inherited = VillagerPotentialAttachments.inherit(
                firstParent,
                secondParent,
                child,
                new Random(123L)
        );

        assertEquals(expected, inherited);
        assertNotSame(firstParentState, inherited);
        assertNotSame(secondParentState, inherited);
        assertSame(firstParentState, VillagerPotentialAttachments.get(firstParent));
        assertSame(secondParentState, VillagerPotentialAttachments.get(secondParent));
        verify(firstParent, times(0)).setData(any(Supplier.class), any());
        verify(secondParent, times(0)).setData(any(Supplier.class), any());
        verify(child, times(1)).setData(any(Supplier.class), eq(inherited));
    }

    @Test
    void inheritedChildIsInitializedOnlyOnceWithFreshAptitudeState() {
        Villager firstParent = villagerWith(
                stateWithEveryAptitude(1.2),
                FIRST_VILLAGER_ID,
                WORLD_SEED
        );
        Villager secondParent = villagerWith(
                stateWithEveryAptitude(0.9),
                SECOND_VILLAGER_ID,
                WORLD_SEED
        );
        Villager child = villagerWith(
                null,
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                WORLD_SEED
        );

        VillagerPotentialState inherited = VillagerPotentialAttachments.inherit(
                firstParent,
                secondParent,
                child,
                new Random(456L)
        );
        VillagerPotentialEvents.onEntityJoinLevel(
                new EntityJoinLevelEvent(child, child.level(), false)
        );
        VillagerPotentialState repeated = VillagerPotentialAttachments.inherit(
                firstParent,
                secondParent,
                child,
                new Random(789L)
        );

        assertSame(inherited, repeated);
        assertEquals(VillagerPotentialState.CURRENT_SCHEMA_VERSION, inherited.schemaVersion());
        assertEquals(
                Set.copyOf(VillagerProfessionIds.supportedVanillaProfessions()),
                inherited.aptitudes().keySet()
        );
        verify(child, times(1)).setData(any(Supplier.class), eq(inherited));
    }

    @Test
    void missingParentPotentialUsesSafeInitializationFallback() {
        Villager firstParent = villagerWith(null, FIRST_VILLAGER_ID, WORLD_SEED);
        Villager secondParent = villagerWith(null, SECOND_VILLAGER_ID, WORLD_SEED);
        Villager child = villagerWith(
                null,
                UUID.fromString("00000000-0000-0000-0000-000000000005"),
                WORLD_SEED
        );

        VillagerPotentialState inherited = VillagerPotentialAttachments.inherit(
                firstParent,
                secondParent,
                child,
                new Random(987L)
        );

        assertEquals(13, inherited.aptitudes().size());
        assertEquals(13, VillagerPotentialAttachments.get(firstParent).aptitudes().size());
        assertEquals(13, VillagerPotentialAttachments.get(secondParent).aptitudes().size());
        verify(firstParent, times(1)).setData(any(Supplier.class), any());
        verify(secondParent, times(1)).setData(any(Supplier.class), any());
        verify(child, times(1)).setData(any(Supplier.class), eq(inherited));
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
    void versionTwoAptitudesMigrateWithoutCareerHistory() {
        ProfessionId librarian = ProfessionId.parse("minecraft:librarian");
        CompoundTag serialized = new CompoundTag();
        serialized.putInt("schema_version", 2);
        CompoundTag aptitudes = new CompoundTag();
        aptitudes.putDouble(librarian.toString(), 0.75);
        serialized.put("aptitudes", aptitudes);

        VillagerPotentialState restored = VillagerPotentialAttachments.CODEC
                .parse(NbtOps.INSTANCE, serialized)
                .getOrThrow();

        assertEquals(0.75, restored.aptitudeFor(librarian).orElseThrow());
        assertTrue(restored.careers().isEmpty());
        assertTrue(restored.activeProfession().isEmpty());
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

    private static VillagerPotentialState stateWithEveryAptitude(double aptitude) {
        Map<ProfessionId, Double> aptitudes = new LinkedHashMap<>();
        VillagerProfessionIds.supportedVanillaProfessions().forEach(
                profession -> aptitudes.put(profession, aptitude)
        );
        return new VillagerPotentialState(VillagerPotentialState.CURRENT_SCHEMA_VERSION, aptitudes);
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
