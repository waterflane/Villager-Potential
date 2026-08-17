package org.waterflane.villager_potential.core;

import java.util.HashMap;
import java.util.List;
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
        Optional<ProfessionId> activeProfession,
        Map<ProfessionId, ProfessionActivityState> professionActivities,
        Map<ProfessionId, TradePaletteState> tradePalettes,
        Map<ProfessionId, Map<TradeKey, MarketDemandState>> marketDemand
) {
    public static final int CURRENT_SCHEMA_VERSION = 10;

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

        Objects.requireNonNull(professionActivities, "professionActivities");
        professionActivities.forEach(VillagerPotentialState::validateProfessionActivity);
        professionActivities = Map.copyOf(professionActivities);

        Objects.requireNonNull(tradePalettes, "tradePalettes");
        tradePalettes.forEach(VillagerPotentialState::validateTradePalette);
        tradePalettes = Map.copyOf(tradePalettes);

        Objects.requireNonNull(marketDemand, "marketDemand");
        Map<ProfessionId, Map<TradeKey, MarketDemandState>> copiedMarketDemand = new HashMap<>();
        marketDemand.forEach((profession, demand) -> {
            validateMarketDemand(profession, demand);
            copiedMarketDemand.put(profession, Map.copyOf(demand));
        });
        marketDemand = Map.copyOf(copiedMarketDemand);
    }

    public VillagerPotentialState(
            int schemaVersion,
            Map<ProfessionId, Double> aptitudes,
            Map<ProfessionId, ProfessionCareerState> careers,
            Optional<ProfessionId> activeProfession,
            Map<ProfessionId, ProfessionActivityState> professionActivities,
            Map<ProfessionId, TradePaletteState> tradePalettes
    ) {
        this(
                schemaVersion,
                aptitudes,
                careers,
                activeProfession,
                professionActivities,
                tradePalettes,
                Map.of()
        );
    }

    public VillagerPotentialState(
            int schemaVersion,
            Map<ProfessionId, Double> aptitudes,
            Map<ProfessionId, ProfessionCareerState> careers,
            Optional<ProfessionId> activeProfession,
            Map<ProfessionId, ProfessionActivityState> professionActivities
    ) {
        this(
                schemaVersion,
                aptitudes,
                careers,
                activeProfession,
                professionActivities,
                Map.of()
        );
    }

    public VillagerPotentialState(
            int schemaVersion,
            Map<ProfessionId, Double> aptitudes,
            Map<ProfessionId, ProfessionCareerState> careers,
            Optional<ProfessionId> activeProfession
    ) {
        this(schemaVersion, aptitudes, careers, activeProfession, Map.of());
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
                Optional.empty(),
                Map.of(),
                Map.of(),
                Map.of()
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
                activeProfession,
                professionActivities,
                tradePalettes,
                marketDemand
        );
    }

    public Optional<ProfessionCareerState> careerFor(ProfessionId professionId) {
        return Optional.ofNullable(careers.get(Objects.requireNonNull(professionId, "professionId")));
    }

    public Optional<SpecializationId> specializationFor(ProfessionId professionId) {
        return careerFor(professionId).flatMap(ProfessionCareerState::specialization);
    }

    /**
     * Stores a stable specialization on an existing profession career.
     */
    public VillagerPotentialState withSpecialization(
            ProfessionId professionId,
            SpecializationId specializationId
    ) {
        Objects.requireNonNull(professionId, "professionId");
        Objects.requireNonNull(specializationId, "specializationId");
        ProfessionCareerState career = careers.get(professionId);
        if (career == null) {
            throw new IllegalStateException(
                    "Specialization requires a career record: " + professionId
            );
        }
        ProfessionCareerState updatedCareer = career.withSpecialization(specializationId);
        return updatedCareer == career ? this : withCareer(professionId, updatedCareer);
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
                activeProfession,
                professionActivities,
                tradePalettes,
                marketDemand
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
                Optional.of(professionId),
                professionActivities,
                tradePalettes,
                marketDemand
        );
    }

    public VillagerPotentialState clearActiveProfession() {
        if (activeProfession.isEmpty()) {
            return this;
        }
        return new VillagerPotentialState(
                schemaVersion,
                aptitudes,
                careers,
                Optional.empty(),
                professionActivities,
                tradePalettes,
                marketDemand
        );
    }

    public Optional<TradePaletteState> tradePaletteFor(ProfessionId professionId) {
        return Optional.ofNullable(
                tradePalettes.get(Objects.requireNonNull(professionId, "professionId"))
        );
    }

    /**
     * Replaces the durable palette for one profession without touching its
     * career, other professions, or the active profession marker.
     */
    public VillagerPotentialState withTradePalette(
            ProfessionId professionId,
            TradePaletteState tradePalette
    ) {
        Objects.requireNonNull(professionId, "professionId");
        Objects.requireNonNull(tradePalette, "tradePalette");
        if (tradePalette.equals(tradePalettes.get(professionId))) {
            return this;
        }

        Map<ProfessionId, TradePaletteState> updatedPalettes = new HashMap<>(tradePalettes);
        updatedPalettes.put(professionId, tradePalette);
        return new VillagerPotentialState(
                schemaVersion,
                aptitudes,
                careers,
                activeProfession,
                professionActivities,
                updatedPalettes,
                marketDemand
        );
    }

    /** Returns demand recorded for one logical trade in exactly one profession. */
    public Optional<MarketDemandState> marketDemandFor(
            ProfessionId professionId,
            TradeKey trade
    ) {
        Objects.requireNonNull(professionId, "professionId");
        Objects.requireNonNull(trade, "trade");
        return Optional.ofNullable(marketDemand.getOrDefault(professionId, Map.of()).get(trade));
    }

    /**
     * Resolves one trade's lazily decayed demand at the supplied
     * server/profession time without updating any stored demand entries.
     */
    public OptionalDouble marketDemandScoreFor(
            ProfessionId professionId,
            TradeKey trade,
            long gameTime,
            MarketDemandConfig config
    ) {
        Objects.requireNonNull(config, "config");
        return marketDemandFor(professionId, trade)
                .map(demand -> OptionalDouble.of(demand.scoreAt(gameTime, config)))
                .orElseGet(OptionalDouble::empty);
    }

    /**
     * Records one completed purchase without changing activity, career state,
     * offer memory, or any pricing data.
     */
    public VillagerPotentialState recordTradePurchase(
            ProfessionId professionId,
            TradeKey trade,
            long gameTime
    ) {
        return recordTradePurchase(
                professionId,
                trade,
                gameTime,
                MarketDemandConfig.DEFAULT
        );
    }

    public VillagerPotentialState recordTradePurchase(
            ProfessionId professionId,
            TradeKey trade,
            long gameTime,
            MarketDemandConfig config
    ) {
        Objects.requireNonNull(professionId, "professionId");
        Objects.requireNonNull(trade, "trade");
        Objects.requireNonNull(config, "config");
        Map<TradeKey, MarketDemandState> professionDemand = marketDemand.getOrDefault(
                professionId,
                Map.of()
        );
        MarketDemandState demand = professionDemand.get(trade);
        MarketDemandState updatedDemand = demand == null
                ? MarketDemandState.firstPurchaseAt(gameTime, config)
                : demand.recordPurchase(gameTime, config);

        Map<TradeKey, MarketDemandState> updatedProfessionDemand =
                new HashMap<>(professionDemand);
        updatedProfessionDemand.put(trade, updatedDemand);
        Map<ProfessionId, Map<TradeKey, MarketDemandState>> updatedMarketDemand =
                new HashMap<>(marketDemand);
        updatedMarketDemand.put(professionId, updatedProfessionDemand);
        return new VillagerPotentialState(
                schemaVersion,
                aptitudes,
                careers,
                activeProfession,
                professionActivities,
                tradePalettes,
                updatedMarketDemand
        );
    }

    /** Records offers materialized for one profession using persistent palette semantics. */
    public VillagerPotentialState recordPresentedTrades(
            ProfessionId professionId,
            List<TradeKey> presentedTrades,
            List<TradeKey> newlyGeneratedTrades,
            long gameTime,
            int maximumHistoryEntries
    ) {
        return recordPresentedTrades(
                professionId,
                presentedTrades,
                newlyGeneratedTrades,
                gameTime,
                maximumHistoryEntries,
                TradePaletteRerollStrategy.PERSISTENT
        );
    }

    public VillagerPotentialState recordPresentedTrades(
            ProfessionId professionId,
            List<TradeKey> presentedTrades,
            List<TradeKey> newlyGeneratedTrades,
            long gameTime,
            int maximumHistoryEntries,
            TradePaletteRerollStrategy strategy
    ) {
        Objects.requireNonNull(professionId, "professionId");
        TradePaletteState palette = tradePalettes.getOrDefault(
                professionId,
                TradePaletteState.empty()
        );
        return withTradePalette(
                professionId,
                palette.recordPresented(
                        presentedTrades,
                        newlyGeneratedTrades,
                        gameTime,
                        maximumHistoryEntries,
                        strategy
                )
        );
    }

    /** Records one completed use against exactly one profession's history. */
    public VillagerPotentialState recordTradeUse(
            ProfessionId professionId,
            TradeKey trade,
            long gameTime,
            int maximumHistoryEntries
    ) {
        Objects.requireNonNull(professionId, "professionId");
        TradePaletteState palette = tradePalettes.getOrDefault(
                professionId,
                TradePaletteState.empty()
        );
        return withTradePalette(
                professionId,
                palette.recordUsed(trade, gameTime, maximumHistoryEntries)
        );
    }

    /**
     * Reads profession-wide recent trade activity at a game time. Offer identity
     * is deliberately absent so this state cannot become per-offer demand.
     */
    public double professionActivityFor(
            ProfessionId professionId,
            long gameTime,
            ProfessionActivityConfig config
    ) {
        Objects.requireNonNull(professionId, "professionId");
        Objects.requireNonNull(config, "config");
        if (!config.enabled()) {
            return 1.0;
        }
        ProfessionActivityState activity = professionActivities.get(professionId);
        return activity == null ? config.baseline() : activity.scoreAt(gameTime, config);
    }

    /**
     * Records one successful trade for exactly one profession. This operation
     * does not touch learned skill, profession time, aptitude, or vanilla XP.
     */
    public VillagerPotentialState recordProfessionTrade(
            ProfessionId professionId,
            long gameTime,
            ProfessionActivityConfig config
    ) {
        Objects.requireNonNull(professionId, "professionId");
        Objects.requireNonNull(config, "config");
        if (!config.enabled()) {
            return this;
        }
        ProfessionActivityState activity = professionActivities.get(professionId);
        ProfessionActivityState updatedActivity = activity == null
                ? ProfessionActivityState.recordFirstTrade(gameTime, config)
                : activity.recordTrade(gameTime, config);
        if (updatedActivity == activity) {
            return this;
        }

        Map<ProfessionId, ProfessionActivityState> updatedActivities =
                new HashMap<>(professionActivities);
        updatedActivities.put(professionId, updatedActivity);
        return new VillagerPotentialState(
                schemaVersion,
                aptitudes,
                careers,
                activeProfession,
                updatedActivities,
                tradePalettes,
                marketDemand
        );
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

    /**
     * Advances tenure and skill for the active profession through the core
     * progression model. Inactive careers are never considered by this operation.
     */
    public VillagerPotentialState progressActiveProfession(
            long elapsedProfessionTime,
            SkillProgressionConfig config
    ) {
        return progressActiveProfession(elapsedProfessionTime, 1.0, config);
    }

    /**
     * Advances only time-based skill, scaled by the supplied activity factor.
     */
    public VillagerPotentialState progressActiveProfession(
            long elapsedProfessionTime,
            double activityFactor,
            SkillProgressionConfig config
    ) {
        Objects.requireNonNull(config, "config");
        if (elapsedProfessionTime < 0L) {
            throw new IllegalArgumentException("elapsedProfessionTime must not be negative");
        }
        if (elapsedProfessionTime == 0L || activeProfession.isEmpty()) {
            return this;
        }

        ProfessionId professionId = activeProfession.orElseThrow();
        Double aptitude = aptitudes.get(professionId);
        if (aptitude == null) {
            throw new IllegalStateException(
                    "Active profession must have an aptitude: " + professionId
            );
        }

        ProfessionCareerState career = careers.get(professionId);
        ProfessionCareerState updatedCareer = career.progressSkill(
                elapsedProfessionTime,
                aptitude,
                activityFactor,
                config
        );
        return updatedCareer == career ? this : withCareer(professionId, updatedCareer);
    }

    /**
     * Resolves the active profession's lazily decayed activity multiplier and
     * applies it to the elapsed profession-time batch.
     */
    public VillagerPotentialState progressActiveProfession(
            long elapsedProfessionTime,
            long gameTime,
            SkillProgressionConfig progressionConfig,
            ProfessionActivityConfig activityConfig
    ) {
        Objects.requireNonNull(activityConfig, "activityConfig");
        if (activeProfession.isEmpty()) {
            return progressActiveProfession(elapsedProfessionTime, 1.0, progressionConfig);
        }
        double activityFactor = professionActivityFor(
                activeProfession.orElseThrow(),
                gameTime,
                activityConfig
        );
        return progressActiveProfession(
                elapsedProfessionTime,
                activityFactor,
                progressionConfig
        );
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
                Optional.empty(),
                Map.of()
        );
    }

    public static VillagerPotentialState migrate(
            int persistedSchemaVersion,
            Map<ProfessionId, Double> persistedAptitudes,
            Map<ProfessionId, ProfessionCareerState> persistedCareers,
            Optional<ProfessionId> persistedActiveProfession
    ) {
        return migrate(
                persistedSchemaVersion,
                persistedAptitudes,
                persistedCareers,
                persistedActiveProfession,
                Map.of(),
                Map.of()
        );
    }

    public static VillagerPotentialState migrate(
            int persistedSchemaVersion,
            Map<ProfessionId, Double> persistedAptitudes,
            Map<ProfessionId, ProfessionCareerState> persistedCareers,
            Optional<ProfessionId> persistedActiveProfession,
            Map<ProfessionId, ProfessionActivityState> persistedProfessionActivities
    ) {
        return migrate(
                persistedSchemaVersion,
                persistedAptitudes,
                persistedCareers,
                persistedActiveProfession,
                persistedProfessionActivities,
                Map.of()
        );
    }

    public static VillagerPotentialState migrate(
            int persistedSchemaVersion,
            Map<ProfessionId, Double> persistedAptitudes,
            Map<ProfessionId, ProfessionCareerState> persistedCareers,
            Optional<ProfessionId> persistedActiveProfession,
            Map<ProfessionId, ProfessionActivityState> persistedProfessionActivities,
            Map<ProfessionId, TradePaletteState> persistedTradePalettes
    ) {
        return migrate(
                persistedSchemaVersion,
                persistedAptitudes,
                persistedCareers,
                persistedActiveProfession,
                persistedProfessionActivities,
                persistedTradePalettes,
                Map.of()
        );
    }

    public static VillagerPotentialState migrate(
            int persistedSchemaVersion,
            Map<ProfessionId, Double> persistedAptitudes,
            Map<ProfessionId, ProfessionCareerState> persistedCareers,
            Optional<ProfessionId> persistedActiveProfession,
            Map<ProfessionId, ProfessionActivityState> persistedProfessionActivities,
            Map<ProfessionId, TradePaletteState> persistedTradePalettes,
            Map<ProfessionId, Map<TradeKey, MarketDemandState>> persistedMarketDemand
    ) {
        return switch (persistedSchemaVersion) {
            case CURRENT_SCHEMA_VERSION -> new VillagerPotentialState(
                    CURRENT_SCHEMA_VERSION,
                    persistedAptitudes,
                    persistedCareers,
                    persistedActiveProfession,
                    persistedProfessionActivities,
                    persistedTradePalettes,
                    persistedMarketDemand
            );
            case 9 -> new VillagerPotentialState(
                    CURRENT_SCHEMA_VERSION,
                    persistedAptitudes,
                    persistedCareers,
                    persistedActiveProfession,
                    persistedProfessionActivities,
                    persistedTradePalettes,
                    persistedMarketDemand
            );
            case 8, 7 -> new VillagerPotentialState(
                    CURRENT_SCHEMA_VERSION,
                    persistedAptitudes,
                    persistedCareers,
                    persistedActiveProfession,
                    persistedProfessionActivities,
                    persistedTradePalettes,
                    Map.of()
            );
            case 6, 5 -> new VillagerPotentialState(
                    CURRENT_SCHEMA_VERSION,
                    persistedAptitudes,
                    persistedCareers,
                    persistedActiveProfession,
                    persistedProfessionActivities,
                    Map.of()
            );
            case 4 -> new VillagerPotentialState(
                    CURRENT_SCHEMA_VERSION,
                    persistedAptitudes,
                    persistedCareers,
                    persistedActiveProfession,
                    Map.of(),
                    Map.of()
            );
            case 3 -> new VillagerPotentialState(
                    CURRENT_SCHEMA_VERSION,
                    persistedAptitudes,
                    persistedCareers,
                    persistedActiveProfession,
                    Map.of(),
                    Map.of()
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

    private static void validateProfessionActivity(
            ProfessionId professionId,
            ProfessionActivityState activity
    ) {
        Objects.requireNonNull(professionId, "professionId");
        Objects.requireNonNull(activity, "activity");
    }

    private static void validateTradePalette(
            ProfessionId professionId,
            TradePaletteState tradePalette
    ) {
        Objects.requireNonNull(professionId, "professionId");
        Objects.requireNonNull(tradePalette, "tradePalette");
    }

    private static void validateMarketDemand(
            ProfessionId professionId,
            Map<TradeKey, MarketDemandState> professionDemand
    ) {
        Objects.requireNonNull(professionId, "professionId");
        Objects.requireNonNull(professionDemand, "professionDemand");
        professionDemand.forEach((trade, demand) -> {
            Objects.requireNonNull(trade, "trade");
            Objects.requireNonNull(demand, "demand");
        });
    }
}
