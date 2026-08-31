package org.waterflane.villager_potential;

import net.minecraft.world.item.ItemStack;

/** Mixin bridge for a transient demand adjustment to an offer's result count. */
public interface DemandPriceOffer {
    ItemStack villagerPotential$baseResult();

    int villagerPotential$baseResultCount();

    void villagerPotential$clearDemandPriceAdjustment();

    void villagerPotential$applyDemandInputDelta(int delta);

    int villagerPotential$retainDemandInputPrice(
            int proposedPrice,
            int maximumPrice,
            boolean demandActive
    );

    void villagerPotential$clearDemandInputPriceFloor();

    void villagerPotential$setEffectiveResultCount(int resultCount);

    void villagerPotential$resetDemandPrice();
}
