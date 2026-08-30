package org.waterflane.villager_potential;

/**
 * Bridge to vanilla's delayed merchant career update.
 */
public interface VillagerLevelUpAccess {
    /**
     * Queues one vanilla career increase unless one is already pending.
     */
    boolean villagerPotential$queueLevelUp();
}
