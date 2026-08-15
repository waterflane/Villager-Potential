package org.waterflane.villager_potential;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

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
    static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk()
                && !event.getLevel().isClientSide()
                && event.getEntity() instanceof Villager villager) {
            NEW_VILLAGERS.add(villager);
        }
    }

    /**
     * Conversion outcomes join the level before NeoForge copies attachments in
     * LivingConversionEvent.Post. Waiting until the first entity tick prevents a
     * cured villager from receiving a throwaway generated identity at join time.
     */
    @SubscribeEvent
    static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof Villager villager
                && NEW_VILLAGERS.remove(villager)) {
            VillagerPotentialAttachments.get(villager);
        }
    }

    /**
     * NeoForge has no profession-change event, so compare vanilla's final
     * profession after its tick with the profession stored in Potential. This
     * per-entity server event also contributes eligible loaded time to a small
     * batch, without scanning every villager globally or rewriting its
     * attachment every tick.
     */
    @SubscribeEvent
    static void onEntityTickPost(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Villager villager
                && villager.level() instanceof ServerLevel serverLevel) {
            VillagerPotentialAttachments.trackProfession(villager, serverLevel.getGameTime());
        }
    }

    /**
     * The event represents an already completed trade. Only the villager's
     * current profession is recorded; offer identity remains outside activity.
     */
    @SubscribeEvent
    static void onTradeWithVillager(TradeWithVillagerEvent event) {
        if (event.getAbstractVillager() instanceof Villager villager
                && villager.level() instanceof ServerLevel serverLevel) {
            VillagerPotentialAttachments.recordTrade(villager, serverLevel.getGameTime());
        }
    }

    @SubscribeEvent
    static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()
                && event.getEntity() instanceof Villager villager) {
            VillagerPotentialAttachments.flushProfessionProgress(villager);
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
}
