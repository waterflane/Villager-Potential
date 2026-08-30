package org.waterflane.villager_potential;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.TradeProgressSnapshot;
import org.waterflane.villager_potential.core.VillagerPotentialConfig;
import org.waterflane.villager_potential.core.VillagerPotentialState;

/** Synchronizes the current profession progression while a player is trading. */
public final class VillagerTradeProgressNetworking {
    private static final String NETWORK_VERSION = "3";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Villager_potential.MODID, "main"),
            () -> NETWORK_VERSION,
            NetworkRegistry.acceptMissingOr(NETWORK_VERSION),
            NetworkRegistry.acceptMissingOr(NETWORK_VERSION)
    );
    private static boolean registered;

    private VillagerTradeProgressNetworking() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        CHANNEL.messageBuilder(
                        VillagerTradeProgressPayload.class,
                        0,
                        NetworkDirection.PLAY_TO_CLIENT
                )
                .encoder(VillagerTradeProgressPayload::encode)
                .decoder(VillagerTradeProgressPayload::decode)
                .consumerMainThread((payload, context) ->
                        VillagerTradeProgressClientState.accept(payload))
                .add();
        registered = true;
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
        if (!CHANNEL.isRemotePresent(player.connection.connection)) {
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

        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new VillagerTradeProgressPayload(villager.getId(), progress)
        );
    }
}
