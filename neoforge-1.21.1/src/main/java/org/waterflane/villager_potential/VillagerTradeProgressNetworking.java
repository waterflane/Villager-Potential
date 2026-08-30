package org.waterflane.villager_potential;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.TradeProgressSnapshot;
import org.waterflane.villager_potential.core.VillagerPotentialConfig;
import org.waterflane.villager_potential.core.VillagerPotentialState;

/** Synchronizes the current profession progression while a player is trading. */
public final class VillagerTradeProgressNetworking {
    private static final String NETWORK_VERSION = "3";
    private VillagerTradeProgressNetworking() {
    }

    static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION).optional();
        registrar.playToClient(
                VillagerTradeProgressPayload.TYPE,
                VillagerTradeProgressPayload.STREAM_CODEC,
                (payload, context) -> VillagerTradeProgressClientState.accept(payload)
        );
    }

    static void syncTradingPlayer(Villager villager, VillagerPotentialState state, long gameTime) {
        if (villager.getTradingPlayer() instanceof ServerPlayer player) {
            syncPlayer(villager, player, state, gameTime);
        }
    }

    static void syncPlayer(
            Villager villager,
            ServerPlayer player,
            VillagerPotentialState state,
            long gameTime
    ) {
        if (!NetworkRegistry.hasChannel(
                        player.connection,
                        VillagerTradeProgressPayload.TYPE.id()
                )) {
            return;
        }

        ProfessionId profession = VillagerPotentialAttachments.toCareerProfession(
                villager.getVillagerData().getProfession()
        );
        if (profession == null) {
            return;
        }
        VillagerPotentialConfig config = ServerConfig.gameplayConfig();
        int level = Math.max(1, Math.min(5, villager.getVillagerData().getLevel()));
        boolean eligible = VillagerJobSiteAccess.hasUsableJobSite(villager, gameTime)
                && ProfessionTenureEligibility.canAccumulate(villager, config.career());
        TradeProgressSnapshot progress = TradeProgressSnapshot.create(
                state,
                profession,
                level,
                gameTime,
                eligible,
                config
        ).orElse(null);
        if (progress == null) {
            return;
        }

        PacketDistributor.sendToPlayer(player, new VillagerTradeProgressPayload(
                villager.getId(),
                progress
        ));
    }
}
