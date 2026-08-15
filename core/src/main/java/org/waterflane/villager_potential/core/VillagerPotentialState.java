package org.waterflane.villager_potential.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Persistent, platform-independent state for Villager Potential.
 */
public record VillagerPotentialState(
        int schemaVersion,
        Map<ProfessionId, Double> aptitudes,
        Map<ProfessionId, ProfessionCareerState> careers,
        Optional<ProfessionId> activeProfession
) {
    public static final int CURRENT_SCHEMA_VERSION = 3;

    public VillagerPotentialState {
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }

        Objects.requireNonNull(aptitudes, "aptitudes");
        aptitudes.forEach(VillagerPotentialState::validateAptitude);
        aptitudes = Map.copyOf(aptitudes);

        Objects.requireNonNull(careers, "careers");
        careers.forEach(VillagerPotentialState::validateCareer);
        careers = Map.copyOf(careers);

        Objects.requireNonNull(activeProfession, "activeProfession");
        if (activeProfession.isPresent() && !careers.containsKey(activeProfession.get())) {
            throw new IllegalArgumentException(
                    "Active profession must have a career record: " + activeProfession.get()
            );
        }
    }

    public VillagerPotentialState(int schemaVersion, Map<ProfessionId, Double> aptitudes) {
        this(schemaVersion, aptitudes, Map.of(), Optional.empty());
    }

    public VillagerPotentialState(int schemaVersion) {
        this(schemaVersion, Map.of(), Map.of(), Optional.empty());
    }

    public static VillagerPotentialState createDefault() {
        return new VillagerPotentialState(
                CURRENT_SCHEMA_VERSION,
                Map.of(),
                Map.of(),
                Optional.empty()
        );
    }

    public OptionalDouble aptitudeFor(ProfessionId professionId) {
        Double aptitude = aptitudes.get(Objects.requireNonNull(professionId, "professionId"));
        return aptitude == null ? OptionalDouble.empty() : OptionalDouble.of(aptitude);
    }

    public VillagerPotentialState withAptitude(ProfessionId professionId, double aptitude) {
        validateAptitude(Objects.requireNonNull(professionId, "professionId"), aptitude);

        Map<ProfessionId, Double> updatedAptitudes = new HashMap<>(aptitudes);
        updatedAptitudes.put(professionId, aptitude);
        return new VillagerPotentialState(
                schemaVersion,
                updatedAptitudes,
                careers,
                activeProfession
        );
    }

    public Optional<ProfessionCareerState> careerFor(ProfessionId professionId) {
        return Optional.ofNullable(careers.get(Objects.requireNonNull(professionId, "professionId")));
    }

    public VillagerPotentialState withCareer(
            ProfessionId professionId,
            ProfessionCareerState career
    ) {
        Objects.requireNonNull(professionId, "professionId");
        Objects.requireNonNull(career, "career");

        Map<ProfessionId, ProfessionCareerState> updatedCareers = new HashMap<>(careers);
        updatedCareers.put(professionId, career);
        return new VillagerPotentialState(
                schemaVersion,
                aptitudes,
                updatedCareers,
                activeProfession
        );
    }

    /**
     * Makes a profession active without discarding any previously held career.
     */
    public VillagerPotentialState assignProfession(
            ProfessionId professionId,
            long assignmentTime
    ) {
        Objects.requireNonNull(professionId, "professionId");
        if (activeProfession.filter(professionId::equals).isPresent()) {
            return this;
        }

        Map<ProfessionId, ProfessionCareerState> updatedCareers = new HashMap<>(careers);
        updatedCareers.compute(
                professionId,
                (ignored, career) -> career == null
                        ? ProfessionCareerState.firstAssignedAt(assignmentTime)
                        : career.reassignedAt(assignmentTime)
        );
        return new VillagerPotentialState(
                schemaVersion,
                aptitudes,
                updatedCareers,
                Optional.of(professionId)
        );
    }

    public VillagerPotentialState clearActiveProfession() {
        if (activeProfession.isEmpty()) {
            return this;
        }
        return new VillagerPotentialState(schemaVersion, aptitudes, careers, Optional.empty());
    }

    /**
     * Adds loaded server ticks to the active career. Eligibility is deliberately
     * decided by the platform layer so future activity rules do not alter this
     * persisted representation.
     */
    public VillagerPotentialState accumulateActiveProfessionTime(long elapsedTicks) {
        if (elapsedTicks < 0) {
            throw new IllegalArgumentException("elapsedTicks must not be negative");
        }
        if (elapsedTicks == 0 || activeProfession.isEmpty()) {
            return this;
        }

        ProfessionId professionId = activeProfession.orElseThrow();
        ProfessionCareerState career = careers.get(professionId);
        ProfessionCareerState updatedCareer = career.accumulateProfessionTime(elapsedTicks);
        return updatedCareer == career ? this : withCareer(professionId, updatedCareer);
    }

    public static VillagerPotentialState migrate(int persistedSchemaVersion) {
        return migrate(persistedSchemaVersion, Map.of());
    }

    public static VillagerPotentialState migrate(
            int persistedSchemaVersion,
            Map<ProfessionId, Double> persistedAptitudes
    ) {
        return migrate(
                persistedSchemaVersion,
                persistedAptitudes,
                Map.of(),
                Optional.empty()
        );
    }

    public static VillagerPotentialState migrate(
            int persistedSchemaVersion,
            Map<ProfessionId, Double> persistedAptitudes,
            Map<ProfessionId, ProfessionCareerState> persistedCareers,
            Optional<ProfessionId> persistedActiveProfession
    ) {
        return switch (persistedSchemaVersion) {
            case CURRENT_SCHEMA_VERSION -> new VillagerPotentialState(
                    persistedSchemaVersion,
                    persistedAptitudes,
                    persistedCareers,
                    persistedActiveProfession
            );
            case 2 -> new VillagerPotentialState(
                    CURRENT_SCHEMA_VERSION,
                    persistedAptitudes,
                    Map.of(),
                    Optional.empty()
            );
            case 1, 0 -> migrateWithoutAptitudesAndCareers();
            default -> throw new IllegalArgumentException(
                    "Unsupported schema version: " + persistedSchemaVersion
            );
        };
    }

    private static VillagerPotentialState migrateWithoutAptitudesAndCareers() {
        return createDefault();
    }

    private static void validateCareer(
            ProfessionId professionId,
            ProfessionCareerState career
    ) {
        Objects.requireNonNull(professionId, "professionId");
        Objects.requireNonNull(career, "career");
    }

    private static void validateAptitude(ProfessionId professionId, Double aptitude) {
        Objects.requireNonNull(professionId, "professionId");
        if (aptitude == null || !Double.isFinite(aptitude)) {
            throw new IllegalArgumentException("Aptitude must be finite for " + professionId);
        }
    }
}
