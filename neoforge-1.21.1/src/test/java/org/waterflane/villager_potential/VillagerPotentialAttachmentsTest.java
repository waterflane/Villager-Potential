package org.waterflane.villager_potential;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.npc.Villager;
import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VillagerPotentialAttachmentsTest {
    @Test
    void stateSurvivesSerializationAndDeserialization() {
        VillagerPotentialState original = new VillagerPotentialState(7);

        Tag serialized = VillagerPotentialAttachments.CODEC
                .encodeStart(NbtOps.INSTANCE, original)
                .getOrThrow();
        VillagerPotentialState restored = VillagerPotentialAttachments.CODEC
                .parse(NbtOps.INSTANCE, serialized)
                .getOrThrow();

        assertEquals(7, ((CompoundTag) serialized).getInt("schema_version"));
        assertEquals(original, restored);
    }

    @Test
    void twoVillagersHaveIndependentState() {
        Villager firstVillager = villagerWith(new VillagerPotentialState(1));
        Villager secondVillager = villagerWith(new VillagerPotentialState(2));

        VillagerPotentialState firstState = VillagerPotentialAttachments.get(firstVillager);
        VillagerPotentialState secondState = VillagerPotentialAttachments.get(secondVillager);

        assertNotSame(firstState, secondState);
        assertEquals(1, firstState.schemaVersion());
        assertEquals(2, secondState.schemaVersion());
    }

    @Test
    void obtainingStateTwiceDoesNotResetIt() {
        VillagerPotentialState storedState = new VillagerPotentialState(3);
        Villager villager = villagerWith(storedState);

        assertSame(storedState, VillagerPotentialAttachments.get(villager));
        assertSame(storedState, VillagerPotentialAttachments.get(villager));
    }

    @SuppressWarnings("unchecked")
    private static Villager villagerWith(VillagerPotentialState state) {
        Villager villager = mock(Villager.class);
        when(villager.getData(any(Supplier.class))).thenReturn(state);
        return villager;
    }
}
