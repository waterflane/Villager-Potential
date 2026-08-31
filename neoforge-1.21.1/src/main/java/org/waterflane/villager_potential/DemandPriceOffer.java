package org.waterflane.villager_potential;

/** Mixin bridge for transient demand adjustments to an offer's input price. */
public interface DemandPriceOffer {
    void villagerPotential$clearDemandPriceAdjustment();

    void villagerPotential$applyDemandInputDelta(int delta);

    int villagerPotential$retainDemandInputPrice(
            int proposedPrice,
            int maximumPrice,
            boolean demandActive
    );

    void villagerPotential$clearDemandInputPriceFloor();

    void villagerPotential$resetDemandPrice();
}
