package org.waterflane.villager_potential;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VillagerJobSiteAccessTest {
    private Villager villager;

    @AfterEach
    void clearCache() {
        if (villager != null) {
            VillagerJobSiteAccess.forget(villager);
        }
    }

    @Test
    void missingWorkstationIsUnavailableImmediately() {
        JobSiteFixture fixture = fixture(Optional.empty());

        assertFalse(VillagerJobSiteAccess.hasUsableJobSite(villager, 100L));
        verify(fixture.serverLevel(), never()).getPoiManager();
    }

    @Test
    void releasingProfessionClearsOnlyVanillaRuntimeState() {
        JobSiteFixture fixture = fixture(Optional.empty());
        VillagerPotentialAttachments.releaseProfession(villager);

        verify(villager).setTradingPlayer(null);
        verify(villager).setVillagerXp(0);
        verify(villager).setVillagerData(fixture.data());
        verify(villager).setOffers(any(MerchantOffers.class));
        verify(villager).refreshBrain(fixture.serverLevel());
    }

    @Test
    void validationNeverLoadsTheWorkstationChunk() {
        JobSiteFixture fixture = fixture(Optional.of(
                GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO)
        ));
        MinecraftServer server = mock(MinecraftServer.class);
        ServerLevel jobLevel = mock(ServerLevel.class);
        when(fixture.serverLevel().getServer()).thenReturn(server);
        when(server.getLevel(Level.OVERWORLD)).thenReturn(jobLevel);
        when(jobLevel.hasChunkAt(BlockPos.ZERO)).thenReturn(false);

        assertFalse(VillagerJobSiteAccess.hasUsableJobSite(villager, 100L, true));
        verify(jobLevel, never()).getPoiManager();
    }

    @Test
    void interactionWithoutWorkstationSilentlyReleasesProfessionAndCancelsTrading() {
        JobSiteFixture fixture = fixture(Optional.empty());
        ServerPlayer player = mock(ServerPlayer.class);
        PlayerInteractEvent.EntityInteract event = new PlayerInteractEvent.EntityInteract(
                player,
                InteractionHand.MAIN_HAND,
                villager
        );

        VillagerPotentialEvents.onVillagerInteract(event);

        assertTrue(event.isCanceled());
        verify(player, never()).displayClientMessage(any(Component.class), eq(true));
        verify(villager).setVillagerData(fixture.data());
        verify(fixture.serverLevel(), never()).getPoiManager();
    }

    @SuppressWarnings("unchecked")
    private JobSiteFixture fixture(Optional<GlobalPos> jobSite) {
        villager = mock(Villager.class);
        VillagerData data = mock(VillagerData.class);
        Brain<Villager> brain = mock(Brain.class);
        ServerLevel serverLevel = mock(ServerLevel.class);
        when(villager.blockPosition()).thenReturn(BlockPos.ZERO);
        when(villager.getVillagerData()).thenReturn(data);
        when(data.getProfession()).thenReturn(VillagerProfession.LIBRARIAN);
        when(data.setProfession(VillagerProfession.NONE)).thenReturn(data);
        when(data.setLevel(1)).thenReturn(data);
        when(villager.getBrain()).thenReturn(brain);
        when(brain.getMemory(MemoryModuleType.JOB_SITE)).thenReturn(jobSite);
        when(villager.level()).thenReturn(serverLevel);
        when(serverLevel.getGameTime()).thenReturn(100L);
        return new JobSiteFixture(serverLevel, data);
    }

    private record JobSiteFixture(ServerLevel serverLevel, VillagerData data) {
    }
}
