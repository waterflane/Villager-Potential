package org.waterflane.villager_potential;

/** Mixin bridge for a transient, server-authoritative offer use ceiling. */
public interface DemandStockOffer {
    int villagerPotential$baseMaximumUses();

    void villagerPotential$setEffectiveMaximumUses(int maximumUses);
}
