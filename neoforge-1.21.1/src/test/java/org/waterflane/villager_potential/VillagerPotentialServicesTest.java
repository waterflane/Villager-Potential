package org.waterflane.villager_potential;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.VillagerPotentialState;
import org.waterflane.villager_potential.core.api.VillagerPotentialService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VillagerPotentialServicesTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void versionedServiceFindsLoadedVillagerByUuidAndReturnsSafeView() {
        UUID villagerId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        ProfessionId profession = ProfessionId.parse("example_mod:alchemist");
        VillagerPotentialState state = new VillagerPotentialState(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                Map.of(profession, 1.2)
        );
        Villager villager = mock(Villager.class);
        ServerLevel level = mock(ServerLevel.class);
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.getAllLevels()).thenReturn(List.of(level));
        when(level.getEntity(villagerId)).thenReturn(villager);
        when(villager.getData(any(Supplier.class))).thenReturn(state);

        VillagerPotentialService service = VillagerPotentialServices.forServer(server);

        assertEquals(1, service.apiVersion());
        assertEquals(1.2, service.find(villagerId).orElseThrow()
                .aptitude(profession).orElseThrow());
        assertTrue(service.find(UUID.randomUUID()).isEmpty());
    }
}
