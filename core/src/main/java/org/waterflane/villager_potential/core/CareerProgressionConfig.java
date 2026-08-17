package org.waterflane.villager_potential.core;

/** Platform-independent rules selecting which loaded ticks count as profession tenure. */
public record CareerProgressionConfig(
        boolean enabled,
        boolean adultsOnly,
        boolean requireJobSite,
        boolean requireWorkActivity
) {
}
