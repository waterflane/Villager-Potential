package org.waterflane.villager_potential;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = Villager_potential.MODID)
public final class VillagerPotentialEvents {
    private static final Set<Villager> NEW_VILLAGERS = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>())
    );

    private VillagerPotentialEvents() {
    }

    @SubscribeEvent
    static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(SpecializationDefinitionManager.INSTANCE);
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        VillagerPotentialCommands.register(event.getDispatcher());
    }

    /**
     * Attaches a category while retaining every live listing as the delegate.
     * Running last also gives unknown entries added by other mods the safe
     * {@code general} category instead of guessing from a generated offer.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onVillagerTrades(VillagerTradesEvent event) {
        VanillaTradeClassifications.wrapPool(event.getType(), event.getTrades());
    }

    @SubscribeEvent
    static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk()
                && !event.getLevel().isClientSide()
                && event.getEntity() instanceof Villager villager) {
            NEW_VILLAGERS.add(villager);
        }
    }

    @SubscribeEvent
    static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof Villager villager
                && villager.level() instanceof ServerLevel serverLevel) {
            if (NEW_VILLAGERS.remove(villager)) {
                VillagerPotentialAttachments.get(villager);
            }
            VillagerPotentialAttachments.trackProfession(villager, serverLevel.getGameTime());
        }
    }

    /**
     * The event represents an already completed trade. It updates both the
     * profession-wide activity score and the exact logical offer's use history.
     */
    @SubscribeEvent
    static void onTradeWithVillager(TradeWithVillagerEvent event) {
        if (event.getAbstractVillager() instanceof Villager villager
                && villager.level() instanceof ServerLevel serverLevel) {
            VillagerPotentialAttachments.recordTrade(
                    villager,
                    event.getMerchantOffer(),
                    serverLevel.getGameTime(),
                    Config.tradeHistoryMaximumEntries()
            );
            if (!BulkTradeContext.active()
                    || Config.marketDemandPriceConfig().dynamicShiftPricing()) {
                VillagerDemandPricing.applyAndSync(villager);
            }
        }
    }

    /** Blocks workstation-less trading, otherwise adds the optional progression hints. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onVillagerInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() == InteractionHand.MAIN_HAND
                && event.getEntity() instanceof ServerPlayer player
                && event.getTarget() instanceof Villager villager) {
            if (!VillagerJobSiteAccess.hasUsableJobSite(
                    villager,
                    villager.level().getGameTime(),
                    true
            )) {
                if (VillagerPotentialAttachments.toCareerProfession(
                        villager.getVillagerData().getProfession()
                ) != null) {
                    VillagerPotentialAttachments.releaseProfession(villager);
                }
                event.setCancellationResult(InteractionResult.CONSUME);
                event.setCanceled(true);
                return;
            }
            VillagerPotentialState state = VillagerPotentialAttachments.get(villager);
            VillagerTradeProgressNetworking.syncPlayer(
                    villager,
                    player,
                    state,
                    villager.level().getGameTime()
            );
        }
    }

    @SubscribeEvent
    static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()
                && event.getEntity() instanceof Villager villager) {
            VillagerPotentialAttachments.flushProfessionProgress(villager);
            VillagerJobSiteAccess.forget(villager);
        }
    }

    @SubscribeEvent
    static void onLivingConversionPre(LivingConversionEvent.Pre event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        if (event.getEntity() instanceof Villager villager
                && event.getOutcome() == EntityType.ZOMBIE_VILLAGER) {
            VillagerPotentialAttachments.flushProfessionProgress(villager);
            VillagerPotentialAttachments.get(villager);
        } else if (event.getEntity() instanceof ZombieVillager zombieVillager
                && event.getOutcome() == EntityType.VILLAGER) {
            VillagerPotentialAttachments.get(zombieVillager);
        }
    }

    @SubscribeEvent
    static void onLivingConversionPost(LivingConversionEvent.Post event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if ((event.getEntity() instanceof Villager
                && event.getOutcome() instanceof ZombieVillager)
                || (event.getEntity() instanceof ZombieVillager
                && event.getOutcome() instanceof Villager)) {
            VillagerPotentialAttachments.store(
                    event.getOutcome(),
                    VillagerPotentialAttachments.stored(event.getEntity())
            );
        }
    }
}
