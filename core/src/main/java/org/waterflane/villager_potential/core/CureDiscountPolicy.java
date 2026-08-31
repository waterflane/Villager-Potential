package org.waterflane.villager_potential.core;

/** Shared rule that allows one permanent cure discount per villager and player. */
public final class CureDiscountPolicy {
    private CureDiscountPolicy() {
    }

    public static boolean shouldApplyCureBonus(int existingPermanentReputation) {
        return existingPermanentReputation <= 0;
    }
}
