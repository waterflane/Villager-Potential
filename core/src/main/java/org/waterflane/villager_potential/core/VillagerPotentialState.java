package org.waterflane.villager_potential.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Persistent, platform-independent state for Villager Potential.
 */
public record VillagerPotentialState(int schemaVersion, Map<ProfessionId, Double> aptitudes) {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    public VillagerPotentialState {
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }

        Objects.requireNonNull(aptitudes, "aptitudes");
        aptitudes.forEach(VillagerPotentialState::validateAptitude);
        aptitudes = Map.copyOf(aptitudes);
    }

    public VillagerPotentialState(int schemaVersion) {
        this(schemaVersion, Map.of());
    }

    public static VillagerPotentialState createDefault() {
        return new VillagerPotentialState(CURRENT_SCHEMA_VERSION, Map.of());
    }

    public OptionalDouble aptitudeFor(ProfessionId professionId) {
        Double aptitude = aptitudes.get(Objects.requireNonNull(professionId, "professionId"));
        return aptitude == null ? OptionalDouble.empty() : OptionalDouble.of(aptitude);
    }

    public VillagerPotentialState withAptitude(ProfessionId professionId, double aptitude) {
        validateAptitude(Objects.requireNonNull(professionId, "professionId"), aptitude);

        Map<ProfessionId, Double> updatedAptitudes = new HashMap<>(aptitudes);
        updatedAptitudes.put(professionId, aptitude);
        return new VillagerPotentialState(schemaVersion, updatedAptitudes);
    }

    public static VillagerPotentialState migrate(int persistedSchemaVersion) {
        return migrate(persistedSchemaVersion, Map.of());
    }

    public static VillagerPotentialState migrate(
            int persistedSchemaVersion,
            Map<ProfessionId, Double> persistedAptitudes
    ) {
        return switch (persistedSchemaVersion) {
            case CURRENT_SCHEMA_VERSION -> new VillagerPotentialState(
                    persistedSchemaVersion,
                    persistedAptitudes
            );
            case 1, 0 -> migrateWithoutAptitudes();
            default -> throw new IllegalArgumentException(
                    "Unsupported schema version: " + persistedSchemaVersion
            );
        };
    }

    private static VillagerPotentialState migrateWithoutAptitudes() {
        return createDefault();
    }

    private static void validateAptitude(ProfessionId professionId, Double aptitude) {
        Objects.requireNonNull(professionId, "professionId");
        if (aptitude == null || !Double.isFinite(aptitude)) {
            throw new IllegalArgumentException("Aptitude must be finite for " + professionId);
        }
    }
}
