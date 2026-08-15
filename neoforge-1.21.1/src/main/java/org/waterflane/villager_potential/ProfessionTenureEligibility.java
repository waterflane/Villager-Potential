package org.waterflane.villager_potential;

import net.minecraft.world.entity.npc.Villager;

import java.util.Objects;

/**
 * Selects the loaded server ticks that count toward profession tenure.
 * Additional job-site or work-activity policies can be composed here without
 * changing the persisted career data.
 */
@FunctionalInterface
interface ProfessionTenureEligibility {
    ProfessionTenureEligibility ADULT = villager -> !villager.isBaby();

    boolean canAccumulate(Villager villager);

    default ProfessionTenureEligibility and(ProfessionTenureEligibility other) {
        Objects.requireNonNull(other, "other");
        return villager -> canAccumulate(villager) && other.canAccumulate(villager);
    }
}
