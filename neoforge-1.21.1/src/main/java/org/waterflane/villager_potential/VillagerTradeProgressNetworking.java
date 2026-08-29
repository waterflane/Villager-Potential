package org.waterflane.villager_potential;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.waterflane.villager_potential.core.ProfessionActivityConfig;
import org.waterflane.villager_potential.core.ProfessionCareerState;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.ProfessionLevelThresholds;
import org.waterflane.villager_potential.core.SkillProgression;
import org.waterflane.villager_potential.core.SkillProgressionConfig;
import org.waterflane.villager_potential.core.VillagerPotentialConfig;
import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.Optional;

/** Synchronizes the current profession progression while a player is trading. */
public final class VillagerTradeProgressNetworking {
    private static final String NETWORK_VERSION = "1";
    private static final double SERVER_TICKS_PER_MINUTE = 20.0 * 60.0;

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
        if (profession == null || !state.activeProfession().equals(Optional.of(profession))) {
            return;
        }
        ProfessionCareerState career = state.careerFor(profession).orElse(null);
        Double aptitude = state.aptitudes().get(profession);
        if (career == null || aptitude == null) {
            return;
        }

        VillagerPotentialConfig config = ServerConfig.gameplayConfig();
        SkillProgressionConfig skillConfig = config.skill();
        ProfessionActivityConfig activityConfig = config.activity();
        ProfessionLevelThresholds thresholds = skillConfig.professionLevelThresholds();
        int level = Math.max(1, Math.min(5, villager.getVillagerData().getLevel()));
        double levelStart = thresholds.thresholdForLevel(level);
        double nextLevel = level == ProfessionLevelThresholds.MASTER_LEVEL
                ? thresholds.masterSkill()
                : thresholds.thresholdForLevel(level + 1);
        double activity = state.professionActivityFor(profession, gameTime, activityConfig);
        boolean eligible = VillagerJobSiteAccess.hasUsableJobSite(villager, gameTime)
                && ProfessionTenureEligibility.canAccumulate(villager, config.career());
        double effectiveAptitude = 1.0
                + (aptitude - 1.0) * skillConfig.aptitudeInfluence();
        double skillPerMinute = skillConfig.enabled()
                && eligible
                && career.learnedSkill() < skillConfig.maximumSkill()
                ? SERVER_TICKS_PER_MINUTE
                * skillConfig.progressionRate()
                * effectiveAptitude
                * activity
                * SkillProgression.professionLevelRateMultiplier(level)
                : 0.0;

        PacketDistributor.sendToPlayer(player, new VillagerTradeProgressPayload(
                villager.getId(),
                level,
                career.learnedSkill(),
                levelStart,
                nextLevel,
                skillPerMinute,
                activity,
                activityConfig.baseline(),
                activityConfig.maximum(),
                activityConfig.increasePerTrade()
        ));
    }
}
