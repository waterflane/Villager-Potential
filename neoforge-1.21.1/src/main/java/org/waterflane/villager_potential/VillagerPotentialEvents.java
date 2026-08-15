package org.waterflane.villager_potential;

import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = Villager_potential.MODID)
public final class VillagerPotentialEvents {
    private VillagerPotentialEvents() {
    }

    @SubscribeEvent
    static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk()
                && !event.getLevel().isClientSide()
                && event.getEntity() instanceof Villager villager) {
            VillagerPotentialAttachments.get(villager);
        }
    }
}
