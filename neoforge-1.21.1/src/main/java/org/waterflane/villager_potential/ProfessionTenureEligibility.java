package org.waterflane.villager_potential;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import org.waterflane.villager_potential.core.CareerProgressionConfig;

import java.util.Objects;

/**
 * Selects the loaded server ticks that count toward profession tenure.
 * Additional job-site or work-activity policies can be composed here without
 * changing the persisted career data.
 */
@FunctionalInterface
interface ProfessionTenureEligibility {
    static ProfessionTenureEligibility from(CareerProgressionConfig config) {
        Objects.requireNonNull(config, "config");
        return villager -> config.enabled()
                && (!config.adultsOnly() || !villager.isBaby())
                && (!config.requireJobSite()
                || villager.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE))
                && (!config.requireWorkActivity()
                || villager.getBrain().getActiveNonCoreActivity().filter(Activity.WORK::equals).isPresent());
    }

    boolean canAccumulate(Villager villager);

    default ProfessionTenureEligibility and(ProfessionTenureEligibility other) {
        Objects.requireNonNull(other, "other");
        return villager -> canAccumulate(villager) && other.canAccumulate(villager);
    }
}
