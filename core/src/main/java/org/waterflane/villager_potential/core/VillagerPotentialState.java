package org.waterflane.villager_potential.core;

/**
 * Persistent, platform-independent state for Villager Potential.
 */
public record VillagerPotentialState(int schemaVersion) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public VillagerPotentialState {
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
    }

    public static VillagerPotentialState createDefault() {
        return new VillagerPotentialState(CURRENT_SCHEMA_VERSION);
    }
}
