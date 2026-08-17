package org.waterflane.villager_potential.core;

import java.util.Objects;

/**
 * Complete loader-neutral server configuration published to gameplay services.
 *
 * <p>Every nested configuration validates its own model invariants while it is
 * constructed, so an instance of this aggregate is a usable, immutable runtime
 * snapshot.</p>
 */
public record VillagerPotentialConfiguration(
        VillagerPotentialConfig gameplay,
        VillagerTradeConfig trades
) {
    public static final VillagerPotentialConfiguration DEFAULT =
            new VillagerPotentialConfiguration(
                    VillagerPotentialConfig.DEFAULT,
                    VillagerTradeConfig.DEFAULT
            );

    public VillagerPotentialConfiguration {
        Objects.requireNonNull(gameplay, "gameplay");
        Objects.requireNonNull(trades, "trades");
    }
}
