package org.waterflane.villager_potential;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.npc.Villager;
import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VillagerPotentialAttachmentsTest {
    @Test
    void oldVillagerReceivesDefaultStateWhenFirstRequested() {
        Villager villager = villagerWith(null);

        VillagerPotentialState state = VillagerPotentialAttachments.get(villager);

        assertEquals(VillagerPotentialState.createDefault(), state);
    }

    @Test
    void repeatedAccessPreservesPersistedState() {
        VillagerPotentialState storedState = new VillagerPotentialState(3);
        Villager villager = villagerWith(storedState);

        assertSame(storedState, VillagerPotentialAttachments.get(villager));
        assertSame(storedState, VillagerPotentialAttachments.get(villager));
    }

    @Test
    void currentSchemaRoundTripsUnchanged() {
        VillagerPotentialState original = VillagerPotentialState.createDefault()
                .withAptitude(ProfessionId.parse("minecraft:librarian"), 0.75)
                .withAptitude(ProfessionId.parse("example_mod:engineer"), 1.25);

        Tag serialized = VillagerPotentialAttachments.CODEC
                .encodeStart(NbtOps.INSTANCE, original)
                .getOrThrow();
        VillagerPotentialState restored = VillagerPotentialAttachments.CODEC
                .parse(NbtOps.INSTANCE, serialized)
                .getOrThrow();
        Villager loadedVillager = villagerWith(restored);

        assertEquals(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                ((CompoundTag) serialized).getInt("schema_version")
        );
        assertEquals(original, restored);
        assertEquals(
                0.75,
                restored.aptitudeFor(ProfessionId.parse("minecraft:librarian")).orElseThrow()
        );
        assertEquals(
                1.25,
                restored.aptitudeFor(ProfessionId.parse("example_mod:engineer")).orElseThrow()
        );
        assertSame(restored, VillagerPotentialAttachments.get(loadedVillager));
        assertSame(restored, VillagerPotentialAttachments.get(loadedVillager));
    }

    @Test
    void olderSchemaUsesMigrationPath() {
        CompoundTag serialized = new CompoundTag();
        serialized.putInt("schema_version", 1);

        VillagerPotentialState restored = VillagerPotentialAttachments.CODEC
                .parse(NbtOps.INSTANCE, serialized)
                .getOrThrow();

        assertEquals(VillagerPotentialState.createDefault(), restored);
        assertTrue(restored.aptitudes().isEmpty());
    }

    @Test
    void syntheticVersionZeroStillUsesMigrationPath() {
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

    @Test
    void differentVillagersInitializeIndependently() {
        Villager firstVillager = villagerWith(null);
        Villager secondVillager = villagerWith(null);

        VillagerPotentialState firstState = VillagerPotentialAttachments.get(firstVillager);
        VillagerPotentialState secondState = VillagerPotentialAttachments.get(secondVillager);

        assertNotSame(firstState, secondState);
        assertEquals(VillagerPotentialState.createDefault(), firstState);
        assertEquals(VillagerPotentialState.createDefault(), secondState);
    }

    @SuppressWarnings("unchecked")
    private static Villager villagerWith(VillagerPotentialState state) {
        Villager villager = mock(Villager.class);
        AtomicReference<VillagerPotentialState> storedState = new AtomicReference<>(state);
        when(villager.getData(any(Supplier.class))).thenAnswer(ignored -> {
            VillagerPotentialState existingState = storedState.get();
            if (existingState != null) {
                return existingState;
            }

            VillagerPotentialState initializedState = VillagerPotentialState.createDefault();
            storedState.set(initializedState);
            return initializedState;
        });
        return villager;
    }
}
