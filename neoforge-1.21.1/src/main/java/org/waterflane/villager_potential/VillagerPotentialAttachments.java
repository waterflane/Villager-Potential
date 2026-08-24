package org.waterflane.villager_potential;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.waterflane.villager_potential.core.AptitudeGenerationConfig;
import org.waterflane.villager_potential.core.AptitudeGenerator;
import org.waterflane.villager_potential.core.AptitudeInheritance;
import org.waterflane.villager_potential.core.AptitudeInheritanceConfig;
import org.waterflane.villager_potential.core.AptitudeProvisioning;
import org.waterflane.villager_potential.core.MarketDemandState;
import org.waterflane.villager_potential.core.ProfessionActivityConfig;
import org.waterflane.villager_potential.core.ProfessionActivityState;
import org.waterflane.villager_potential.core.ProfessionCareerState;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.ProfessionSpecializationAssignment;
import org.waterflane.villager_potential.core.SkillProgression;
import org.waterflane.villager_potential.core.SkillProgressionConfig;
import org.waterflane.villager_potential.core.SpecializationId;
import org.waterflane.villager_potential.core.TradeHistory;
import org.waterflane.villager_potential.core.TradeKey;
import org.waterflane.villager_potential.core.TradeMemoryRecovery;
import org.waterflane.villager_potential.core.TradePaletteState;
import org.waterflane.villager_potential.core.TradePaletteRerollStrategy;
import org.waterflane.villager_potential.core.VillagerPotentialState;
import org.waterflane.villager_potential.core.VillagerPotentialConfig;
import org.waterflane.villager_potential.core.api.PotentialView;
import org.waterflane.villager_potential.core.api.PotentialViews;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.random.RandomGenerator;

public final class VillagerPotentialAttachments {
    static final long PROFESSION_PROGRESS_INTERVAL_TICKS = 20L;
    static final SkillProgressionConfig SKILL_PROGRESSION_CONFIG =
            VillagerPotentialConfig.DEFAULT.skill();
    static final ProfessionActivityConfig PROFESSION_ACTIVITY_CONFIG =
            VillagerPotentialConfig.DEFAULT.activity();
    private static final Map<Villager, ProfessionProgressBatch> PROFESSION_PROGRESS_BATCHES =
            new WeakHashMap<>();
    static final AptitudeGenerationConfig APTITUDE_CONFIG =
            VillagerPotentialConfig.DEFAULT.aptitude();
    static final AptitudeInheritanceConfig INHERITANCE_CONFIG =
            VillagerPotentialConfig.DEFAULT.inheritance();
    private static final long INITIALIZATION_SALT = 0x56494C4C41474552L;
    private static final long INHERITANCE_SALT = 0x494E484552495453L;
    private static final long SPECIALIZATION_SALT = 0x5350454349414C53L;
    private static final long LAZY_APTITUDE_SALT = 0x4150544954554445L;
    private static final Codec<ProfessionId> PROFESSION_ID_CODEC = Codec.STRING.comapFlatMap(
            VillagerPotentialAttachments::parseProfessionId,
            ProfessionId::toString
    );
    private static final Codec<Map<ProfessionId, Double>> APTITUDES_CODEC = Codec.unboundedMap(
            PROFESSION_ID_CODEC,
            Codec.DOUBLE
    );
    private static final Codec<SpecializationId> SPECIALIZATION_ID_CODEC = Codec.STRING.comapFlatMap(
            VillagerPotentialAttachments::parseSpecializationId,
            SpecializationId::toString
    );
    private static final Codec<ProfessionCareerState> CAREER_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("accumulated_profession_time")
                    .forGetter(ProfessionCareerState::accumulatedProfessionTime),
            Codec.DOUBLE.fieldOf("learned_skill").forGetter(ProfessionCareerState::learnedSkill),
            Codec.LONG.fieldOf("first_assignment").forGetter(ProfessionCareerState::firstAssignment),
            Codec.LONG.fieldOf("latest_assignment").forGetter(ProfessionCareerState::latestAssignment),
            SPECIALIZATION_ID_CODEC.optionalFieldOf("specialization")
                    .forGetter(ProfessionCareerState::specialization)
    ).apply(instance, ProfessionCareerState::new));
    private static final Codec<Map<ProfessionId, ProfessionCareerState>> CAREERS_CODEC =
            Codec.unboundedMap(PROFESSION_ID_CODEC, CAREER_CODEC);
    private static final Codec<ProfessionActivityState> PROFESSION_ACTIVITY_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.DOUBLE.fieldOf("score").forGetter(ProfessionActivityState::score),
                    Codec.LONG.fieldOf("last_update_game_time")
                            .forGetter(ProfessionActivityState::lastUpdateGameTime)
            ).apply(instance, ProfessionActivityState::new));
    private static final Codec<Map<ProfessionId, ProfessionActivityState>>
            PROFESSION_ACTIVITIES_CODEC = Codec.unboundedMap(
                    PROFESSION_ID_CODEC,
                    PROFESSION_ACTIVITY_CODEC
            );
    private static final Codec<TradeKey.Item> TRADE_ITEM_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("item_id").forGetter(TradeKey.Item::itemId),
                    Codec.INT.fieldOf("count").forGetter(TradeKey.Item::count),
                    Codec.STRING.optionalFieldOf("components", "")
                            .forGetter(TradeKey.Item::components)
            ).apply(instance, TradeKey.Item::new));
    private static final Codec<TradeKey.Offer> OFFER_TRADE_KEY_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    TRADE_ITEM_CODEC.fieldOf("cost_a").forGetter(TradeKey.Offer::costA),
                    TRADE_ITEM_CODEC.optionalFieldOf("cost_b")
                            .forGetter(TradeKey.Offer::costB),
                    TRADE_ITEM_CODEC.fieldOf("result").forGetter(TradeKey.Offer::result)
            ).apply(instance, TradeKey.Offer::new));
    private static final Codec<TradeKey.Fallback> FALLBACK_TRADE_KEY_CODEC =
            Codec.STRING.fieldOf("fallback")
                    .xmap(TradeKey.Fallback::new, TradeKey.Fallback::representation)
                    .codec();
    private static final Codec<TradeKey> TRADE_KEY_CODEC = Codec.either(
            OFFER_TRADE_KEY_CODEC,
            FALLBACK_TRADE_KEY_CODEC
    ).xmap(
            key -> key.map(offer -> (TradeKey) offer, fallback -> fallback),
            key -> {
                if (key instanceof TradeKey.Offer offer) {
                    return Either.left(offer);
                }
                return Either.right((TradeKey.Fallback) key);
            }
    );
    private static final Codec<TradeHistory> TRADE_HISTORY_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.LONG.fieldOf("times_seen").forGetter(TradeHistory::timesSeen),
                    Codec.LONG.optionalFieldOf("last_seen")
                            .forGetter(history -> boxed(history.lastSeen())),
                    Codec.LONG.fieldOf("times_used").forGetter(TradeHistory::timesUsed),
                    Codec.LONG.optionalFieldOf("last_used")
                            .forGetter(history -> boxed(history.lastUsed()))
            ).apply(instance, (timesSeen, lastSeen, timesUsed, lastUsed) -> new TradeHistory(
                    timesSeen,
                    unboxed(lastSeen),
                    timesUsed,
                    unboxed(lastUsed)
            )));
    private static final Codec<PersistedTradeHistory> TRADE_HISTORY_ENTRY_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    TRADE_KEY_CODEC.fieldOf("trade").forGetter(PersistedTradeHistory::trade),
                    TRADE_HISTORY_CODEC.fieldOf("history")
                            .forGetter(PersistedTradeHistory::history)
            ).apply(instance, PersistedTradeHistory::new));
    private static final Codec<TradePaletteState> TRADE_PALETTE_CODEC =
            RecordCodecBuilder.<PersistedTradePalette>create(instance -> instance.group(
                    TRADE_KEY_CODEC.listOf().fieldOf("active_trades")
                            .forGetter(PersistedTradePalette::activeTrades),
                    TRADE_KEY_CODEC.listOf().optionalFieldOf("selection_history", List.of())
                            .forGetter(PersistedTradePalette::selectionHistory),
                    TRADE_HISTORY_ENTRY_CODEC.listOf().optionalFieldOf("offer_history", List.of())
                            .forGetter(PersistedTradePalette::offerHistory)
            ).apply(instance, PersistedTradePalette::new)).xmap(
                    PersistedTradePalette::toState,
                    PersistedTradePalette::fromState
            );
    private static final Codec<Map<ProfessionId, TradePaletteState>> TRADE_PALETTES_CODEC =
            Codec.unboundedMap(PROFESSION_ID_CODEC, TRADE_PALETTE_CODEC);
    private static final Codec<Double> MARKET_DEMAND_SCORE_CODEC = Codec.either(
            Codec.DOUBLE,
            Codec.INT
    ).xmap(
            score -> score.map(value -> value, Integer::doubleValue),
            Either::left
    );
    private static final Codec<MarketDemandState> MARKET_DEMAND_STATE_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    MARKET_DEMAND_SCORE_CODEC.fieldOf("score")
                            .forGetter(MarketDemandState::demandScore),
                    Codec.LONG.fieldOf("times_purchased")
                            .forGetter(MarketDemandState::timesPurchased),
                    Codec.LONG.fieldOf("last_purchase_game_time")
                            .forGetter(MarketDemandState::lastPurchaseGameTime)
            ).apply(instance, MarketDemandState::new));
    private static final Codec<PersistedMarketDemand> MARKET_DEMAND_ENTRY_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    TRADE_KEY_CODEC.fieldOf("trade").forGetter(PersistedMarketDemand::trade),
                    MARKET_DEMAND_STATE_CODEC.fieldOf("demand")
                            .forGetter(PersistedMarketDemand::demand)
            ).apply(instance, PersistedMarketDemand::new));
    private static final Codec<Map<TradeKey, MarketDemandState>> PROFESSION_MARKET_DEMAND_CODEC =
            MARKET_DEMAND_ENTRY_CODEC.listOf().xmap(
                    VillagerPotentialAttachments::marketDemandFromEntries,
                    VillagerPotentialAttachments::marketDemandToEntries
            );
    private static final Codec<Map<ProfessionId, Map<TradeKey, MarketDemandState>>>
            MARKET_DEMAND_CODEC = Codec.unboundedMap(
                    PROFESSION_ID_CODEC,
                    PROFESSION_MARKET_DEMAND_CODEC
            );
    static final Codec<VillagerPotentialState> CODEC = RecordCodecBuilder.<PersistedState>create(instance -> instance.group(
            Codec.INT.fieldOf("schema_version").forGetter(PersistedState::schemaVersion),
            APTITUDES_CODEC.optionalFieldOf("aptitudes", Map.of()).forGetter(PersistedState::aptitudes),
            CAREERS_CODEC.optionalFieldOf("careers", Map.of()).forGetter(PersistedState::careers),
            PROFESSION_ID_CODEC.optionalFieldOf("active_profession")
                    .forGetter(PersistedState::activeProfession),
            PROFESSION_ACTIVITIES_CODEC.optionalFieldOf("profession_activity", Map.of())
                    .forGetter(PersistedState::professionActivities),
            TRADE_PALETTES_CODEC.optionalFieldOf("trade_palettes", Map.of())
                    .forGetter(PersistedState::tradePalettes),
            MARKET_DEMAND_CODEC.optionalFieldOf("market_demand", Map.of())
                    .forGetter(PersistedState::marketDemand)
    ).apply(instance, PersistedState::new)).comapFlatMap(
            persisted -> migrate(
                    persisted.schemaVersion(),
                    persisted.aptitudes(),
                    persisted.careers(),
                    persisted.activeProfession(),
                    persisted.professionActivities(),
                    persisted.tradePalettes(),
                    persisted.marketDemand()
            ),
            state -> new PersistedState(
                    state.schemaVersion(),
                    state.aptitudes(),
                    state.careers(),
                    state.activeProfession(),
                    state.professionActivities(),
                    state.tradePalettes(),
                    state.marketDemand()
            )
    );

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Villager_potential.MODID);
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<VillagerPotentialState>> POTENTIAL =
            ATTACHMENT_TYPES.register("potential", () -> AttachmentType.builder(VillagerPotentialState::createDefault)
                    .serialize(CODEC)
                    .copyOnDeath()
                    .copyHandler(VillagerPotentialAttachments::copyVillagerIdentity)
                    .build());

    private VillagerPotentialAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }

    public static VillagerPotentialState get(Villager villager) {
        Objects.requireNonNull(villager, "villager");
        return getOrInitialize(villager);
    }

    /** Internal persistence entry point used by the supported facade. */
    static VillagerPotentialState assignApiSpecialization(
            Villager villager,
            ProfessionId profession,
            SpecializationId specialization
    ) {
        Objects.requireNonNull(villager, "villager");
        Objects.requireNonNull(profession, "profession");
        Objects.requireNonNull(specialization, "specialization");
        var definition = SpecializationDefinitionManager.INSTANCE.definitionFor(profession)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Profession has no specialization definition: " + profession
                ));
        if (!definition.supports(specialization)) {
            throw new IllegalArgumentException(
                    "Specialization " + specialization + " is not supported by " + profession
            );
        }
        VillagerPotentialState state = get(villager);
        VillagerPotentialState updated = state.withSpecialization(profession, specialization);
        persistAndEmit(villager, state, updated);
        return updated;
    }

    static VillagerPotentialState adminSetAptitude(
            Villager villager,
            ProfessionId profession,
            double aptitude
    ) {
        Objects.requireNonNull(villager, "villager");
        Objects.requireNonNull(profession, "profession");
        if (!Double.isFinite(aptitude) || aptitude < 0.0) {
            throw new IllegalArgumentException("aptitude must be finite and non-negative");
        }
        VillagerPotentialState state = get(villager);
        VillagerPotentialState updated = state.withAptitude(profession, aptitude);
        persistAndEmit(villager, state, updated);
        return updated;
    }

    static VillagerPotentialState adminSetSkill(
            Villager villager,
            ProfessionId profession,
            double skill
    ) {
        Objects.requireNonNull(villager, "villager");
        VillagerPotentialState state = get(villager);
        VillagerPotentialState updated = state.withSkill(
                Objects.requireNonNull(profession, "profession"),
                skill
        );
        persistAndEmit(villager, state, updated);
        return updated;
    }

    static VillagerPotentialState adminResetProfession(
            Villager villager,
            ProfessionId profession
    ) {
        Objects.requireNonNull(villager, "villager");
        VillagerPotentialState state = get(villager);
        VillagerPotentialState updated = state.resetProfessionDerivedState(
                Objects.requireNonNull(profession, "profession")
        );
        PROFESSION_PROGRESS_BATCHES.remove(villager);
        persistAndEmit(villager, state, updated);
        return updated;
    }

    static VillagerPotentialState adminRegenerateProfession(
            Villager villager,
            ProfessionId profession
    ) {
        Objects.requireNonNull(villager, "villager");
        Objects.requireNonNull(profession, "profession");
        VillagerPotentialState state = get(villager);
        double aptitude = AptitudeGenerator.generate(
                ServerConfig.gameplayConfig().aptitude(),
                new Random(villager.getRandom().nextLong())
        );
        VillagerPotentialState updated = state.resetProfessionDerivedState(profession)
                .withAptitude(profession, aptitude);
        PROFESSION_PROGRESS_BATCHES.remove(villager);
        persistAndEmit(villager, state, updated);
        return updated;
    }

    static VillagerPotentialState adminRegenerateAll(Villager villager) {
        Objects.requireNonNull(villager, "villager");
        Random random = new Random(villager.getRandom().nextLong());
        Map<ProfessionId, Double> aptitudes = new LinkedHashMap<>();
        for (ProfessionId profession : VillagerProfessionIds.supportedVanillaProfessions()) {
            aptitudes.put(
                    profession,
                    AptitudeGenerator.generate(ServerConfig.gameplayConfig().aptitude(), random)
            );
        }
        VillagerPotentialState state = get(villager);
        VillagerPotentialState updated = new VillagerPotentialState(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                aptitudes
        );
        PROFESSION_PROGRESS_BATCHES.remove(villager);
        persistAndEmit(villager, state, updated);
        return updated;
    }

    static VillagerPotentialState get(ZombieVillager zombieVillager) {
        Objects.requireNonNull(zombieVillager, "zombieVillager");
        return getOrInitialize(zombieVillager);
    }

    static void trackProfession(Villager villager, long assignmentTime) {
        Objects.requireNonNull(villager, "villager");
        VillagerPotentialConfig config = ServerConfig.gameplayConfig();
        VillagerProfession profession = villager.getVillagerData().getProfession();
        ProfessionId currentProfession = toCareerProfession(profession);
        ProfessionProgressBatch batch = PROFESSION_PROGRESS_BATCHES.get(villager);
        VillagerPotentialState state = null;
        VillagerPotentialState updatedState = null;

        if (batch == null || !Objects.equals(batch.profession(), currentProfession)) {
            state = get(villager);
            updatedState = batch == null
                    ? state
                    : progressMatchingProfession(state, batch, config);
            updatedState = assignProfession(
                    updatedState,
                    currentProfession,
                    assignmentTime,
                    worldSeed(villager),
                    villager.getUUID()
            );
            batch = new ProfessionProgressBatch(
                    currentProfession,
                    0L,
                    assignmentTime,
                    villager.getVillagerData().getLevel()
            );
            PROFESSION_PROGRESS_BATCHES.put(villager, batch);
        }

        batch.observeGameTime(assignmentTime);

        if (currentProfession != null
                && ProfessionTenureEligibility.from(config.career()).canAccumulate(villager)) {
            batch.addElapsedTick();
        }

        if (batch.elapsedProfessionTime() >= PROFESSION_PROGRESS_INTERVAL_TICKS) {
            if (state == null) {
                state = get(villager);
                updatedState = state;
            }
            updatedState = progressMatchingProfession(updatedState, batch, config);
            batch.clearElapsedTime();
        }

        if (state != null && updatedState != state) {
            persistAndEmit(villager, state, updatedState);
        }
        if (updatedState != null) {
            queueEarnedProfessionLevel(villager, updatedState, currentProfession);
        }
        int currentLevel = villager.getVillagerData().getLevel();
        if (currentProfession != null && currentLevel != batch.vanillaLevel()) {
            VillagerPotentialState eventState = updatedState == null ? get(villager) : updatedState;
            VillagerPotentialLifecycleEvents.emitVanillaLevelChanged(
                    new VillagerPotentialLifecycleEvents.VanillaLevelChanged(
                            villager,
                            currentProfession,
                            batch.vanillaLevel(),
                            currentLevel,
                            PotentialViews.snapshot(eventState)
                    )
            );
        }
        batch.observeVanillaLevel(currentLevel);
    }

    static void flushProfessionProgress(Villager villager) {
        Objects.requireNonNull(villager, "villager");
        ProfessionProgressBatch batch = PROFESSION_PROGRESS_BATCHES.remove(villager);
        if (batch == null || batch.elapsedProfessionTime() == 0L) {
            return;
        }

        VillagerPotentialState state = get(villager);
        VillagerPotentialState updatedState = progressMatchingProfession(
                state,
                batch,
                ServerConfig.gameplayConfig()
        );
        if (updatedState != state) {
            persistAndEmit(villager, state, updatedState);
        }
    }

    static void recordTrade(Villager villager, long gameTime) {
        Objects.requireNonNull(villager, "villager");
        ProfessionId profession = toCareerProfession(
                villager.getVillagerData().getProfession()
        );
        if (profession == null) {
            return;
        }

        VillagerPotentialState state = get(villager);
        ProfessionActivityConfig activityConfig = ServerConfig.gameplayConfig().activity();
        VillagerPotentialState updatedState = state.recordProfessionTrade(
                profession,
                gameTime,
                activityConfig
        );
        if (updatedState != state) {
            persistAndEmit(villager, state, updatedState);
        }
    }

    static void recordTrade(
            Villager villager,
            MerchantOffer offer,
            long gameTime,
            int maximumHistoryEntries
    ) {
        Objects.requireNonNull(offer, "offer");
        Objects.requireNonNull(villager, "villager");
        ProfessionId profession = toCareerProfession(
                villager.getVillagerData().getProfession()
        );
        if (profession == null) {
            return;
        }

        VillagerPotentialState state = get(villager);
        VillagerPotentialConfig gameplayConfig = ServerConfig.gameplayConfig();
        TradePaletteRerollStrategy strategy = Config.tradePaletteRerollStrategy();
        long observationTime = tradeMemoryTime(state, profession, gameTime, strategy);
        MerchantOfferTradeKeys.Identity identity = MerchantOfferTradeKeys.identify(offer);
        TradeKey trade = identity.key();
        Optional<MarketDemandState> previousDemand = identity.stable()
                ? state.marketDemandFor(profession, trade)
                : Optional.empty();
        VillagerPotentialState updatedState = state.recordProfessionTrade(
                profession,
                gameTime,
                gameplayConfig.activity()
        );
        if (identity.stable()) {
            updatedState = updatedState.recordTradeUse(
                        profession,
                        trade,
                        observationTime,
                        maximumHistoryEntries
                )
                .recordTradePurchase(
                        profession,
                        trade,
                        gameTime,
                        Config.marketDemandConfig()
                );
        }
        if (updatedState != state) {
            persistAndEmit(villager, state, updatedState);
        }
        PotentialView view = PotentialViews.snapshot(updatedState);
        VillagerPotentialTradeEvents.emitTradeCompleted(
                new VillagerPotentialTradeEvents.TradeCompleted(
                        villager,
                        profession,
                        trade,
                        view
                )
        );
        Optional<MarketDemandState> updatedDemand = identity.stable()
                ? updatedState.marketDemandFor(profession, trade)
                : Optional.empty();
        if (!updatedDemand.equals(previousDemand) && updatedDemand.isPresent()) {
            MarketDemandState demand = updatedDemand.orElseThrow();
            VillagerPotentialDiagnostics.demand(
                    villager.getUUID(),
                    profession,
                    trade,
                    previousDemand.map(MarketDemandState::demandScore)
                            .orElse(Config.marketDemandConfig().baseline()),
                    demand.demandScore()
            );
            VillagerPotentialTradeEvents.emitDemandChanged(
                    new VillagerPotentialTradeEvents.DemandChanged(
                            villager,
                            profession,
                            trade,
                            previousDemand.map(VillagerPotentialAttachments::demandInfo),
                            demandInfo(demand),
                            view
                    )
            );
        }
    }

    /** Records only offers appended by the generation call that just completed. */
    public static void recordGeneratedOffers(
            Villager villager,
            MerchantOffers offers,
            int firstGeneratedIndex,
            long gameTime,
            int maximumHistoryEntries
    ) {
        recordGeneratedOffers(
                villager,
                offers,
                firstGeneratedIndex,
                gameTime,
                maximumHistoryEntries,
                Config.tradePaletteRerollStrategy()
        );
    }

    static void recordGeneratedOffers(
            Villager villager,
            MerchantOffers offers,
            int firstGeneratedIndex,
            long gameTime,
            int maximumHistoryEntries,
            TradePaletteRerollStrategy strategy
    ) {
        Objects.requireNonNull(villager, "villager");
        Objects.requireNonNull(offers, "offers");
        if (firstGeneratedIndex < 0 || firstGeneratedIndex > offers.size()) {
            throw new IllegalArgumentException("firstGeneratedIndex is outside offers");
        }
        if (firstGeneratedIndex == offers.size()) {
            return;
        }

        ProfessionId profession = toCareerProfession(
                villager.getVillagerData().getProfession()
        );
        if (profession == null) {
            return;
        }

        List<TradeKey> presentedTrades = offers.stream()
                .map(MerchantOfferTradeKeys::identify)
                .filter(MerchantOfferTradeKeys.Identity::stable)
                .map(MerchantOfferTradeKeys.Identity::key)
                .toList();
        List<TradeKey> generatedTrades = offers.subList(firstGeneratedIndex, offers.size())
                .stream()
                .map(MerchantOfferTradeKeys::identify)
                .filter(MerchantOfferTradeKeys.Identity::stable)
                .map(MerchantOfferTradeKeys.Identity::key)
                .toList();
        VillagerPotentialState state = get(villager);
        List<TradeKey> previouslyLearned = state.tradePaletteFor(profession)
                .map(TradePaletteState::activeTrades)
                .orElse(List.of());
        if (strategy == TradePaletteRerollStrategy.PERSISTENT) {
            List<TradeKey> learnedTrades = state.tradePaletteFor(profession)
                    .map(TradePaletteState::activeTrades)
                    .orElse(List.of());
            if (firstGeneratedIndex == 0 && !learnedTrades.isEmpty()) {
                generatedTrades = List.of();
            }
        }
        long observationTime = tradeMemoryTime(state, profession, gameTime, strategy);
        if (strategy == TradePaletteRerollStrategy.CYCLIC) {
            TradePaletteState palette = state.tradePaletteFor(profession)
                    .orElse(TradePaletteState.empty());
            boolean resetCycle = TradeMemoryRecovery.shouldResetCycle(
                    palette.offerHistory().values(),
                    observationTime,
                    Config.tradeMemoryRecoveryConfig()
            );
            Set<TradeKey> resetTrades = palette.offerHistory().entrySet().stream()
                    .filter(entry -> resetCycle || Config.isRareTradeProtected(entry.getKey())
                            && TradeMemoryRecovery.effectiveCyclicCount(
                            entry.getValue(),
                            observationTime,
                            Config.tradeMemoryRecoveryConfig(),
                            true
                    ) == 0L)
                    .map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toSet());
            state = state.withTradePalette(profession, palette.resetSeenCounts(resetTrades));
        }
        VillagerPotentialState updatedState = state.recordPresentedTrades(
                profession,
                presentedTrades,
                generatedTrades,
                observationTime,
                maximumHistoryEntries,
                strategy
        );
        if (updatedState != state) {
            persistAndEmit(villager, state, updatedState);
        }
        List<TradeKey> newPaletteEntries = strategy == TradePaletteRerollStrategy.PERSISTENT
                ? updatedState.tradePaletteFor(profession)
                .map(TradePaletteState::activeTrades)
                .orElse(List.of())
                .stream()
                .filter(trade -> !previouslyLearned.contains(trade))
                .toList()
                : List.of();
        if (!newPaletteEntries.isEmpty()) {
            VillagerPotentialTradeEvents.ProcessingKind kind = previouslyLearned.isEmpty()
                    ? VillagerPotentialTradeEvents.ProcessingKind.INITIAL_OR_NEW_LEVEL_GENERATION
                    : strategy == TradePaletteRerollStrategy.PERSISTENT
                    ? VillagerPotentialTradeEvents.ProcessingKind.INITIAL_OR_NEW_LEVEL_GENERATION
                    : VillagerPotentialTradeEvents.ProcessingKind.REROLL;
            VillagerPotentialTradeEvents.emitPaletteEntriesGenerated(
                    new VillagerPotentialTradeEvents.PaletteEntriesGenerated(
                            villager,
                            profession,
                            newPaletteEntries,
                            kind
                    )
            );
            VillagerPotentialDiagnostics.learned(
                    villager.getUUID(),
                    profession,
                    newPaletteEntries.size()
            );
        }
    }

    private static long tradeMemoryTime(
            VillagerPotentialState state,
            ProfessionId profession,
            long gameTime,
            TradePaletteRerollStrategy strategy
    ) {
        return switch (strategy) {
            case WEIGHTED_MEMORY, EXHAUST, CYCLIC -> state.careerFor(profession)
                    .map(ProfessionCareerState::accumulatedProfessionTime)
                    .orElse(0L);
            case PERSISTENT, VANILLA -> gameTime;
        };
    }

    private static ProfessionId toCareerProfession(VillagerProfession profession) {
        return profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT
                ? null
                : VillagerProfessionIds.tryFromMinecraft(profession).orElse(null);
    }

    private static VillagerPotentialState assignProfession(
            VillagerPotentialState state,
            ProfessionId profession,
            long assignmentTime,
            long worldSeed,
            UUID villagerId
    ) {
        if (profession == null) {
            return state.clearActiveProfession();
        }
        VillagerPotentialConfig config = ServerConfig.gameplayConfig();
        VillagerPotentialState provisioned = AptitudeProvisioning.ensure(
                state,
                profession,
                config.aptitude(),
                new Random(lazyAptitudeSeed(worldSeed, villagerId, profession))
        );
        return ProfessionSpecializationAssignment.enterProfession(
                provisioned,
                profession,
                assignmentTime,
                SpecializationDefinitionManager.INSTANCE.definitionFor(profession),
                new Random(specializationSeed(worldSeed, villagerId, profession))
        );
    }

    private static VillagerPotentialState progressMatchingProfession(
            VillagerPotentialState state,
            ProfessionProgressBatch batch,
            VillagerPotentialConfig config
    ) {
        if (batch.elapsedProfessionTime() == 0L
                || !state.activeProfession().equals(Optional.ofNullable(batch.profession()))) {
            return state;
        }
        return state.progressActiveProfession(
                batch.elapsedProfessionTime(),
                batch.lastObservedGameTime(),
                config.skill(),
                config.activity()
        );
    }

    /**
     * Queues at most one transition at a time. If one skill update crosses
     * several thresholds, subsequent transitions are queued only after vanilla
     * has applied the preceding level, so offers cannot be skipped or duplicated.
     */
    static boolean queueEarnedProfessionLevel(
            Villager villager,
            VillagerPotentialState state,
            ProfessionId profession
    ) {
        if (profession == null
                || !state.activeProfession().equals(Optional.of(profession))) {
            return false;
        }

        int currentLevel = villager.getVillagerData().getLevel();
        if (currentLevel < 1 || currentLevel >= 5) {
            return false;
        }

        double learnedSkill = state.careerFor(profession)
                .orElseThrow()
                .learnedSkill();
        int earnedLevel = SkillProgression.vanillaProfessionLevel(
                learnedSkill,
                currentLevel,
                ServerConfig.gameplayConfig().skill()
        );
        return earnedLevel > currentLevel
                && villager instanceof VillagerLevelUpAccess access
                && access.villagerPotential$queueLevelUp();
    }

    private static VillagerPotentialState getOrInitialize(Entity entity) {
        VillagerPotentialState state = entity.getData(POTENTIAL);
        if (!state.aptitudes().isEmpty()) {
            return state;
        }

        VillagerPotentialState initializedState = initialize(
                worldSeed(entity),
                entity.getUUID()
        );
        if (!state.careers().isEmpty()
                || !state.professionActivities().isEmpty()
                || !state.tradePalettes().isEmpty()
                || !state.marketDemand().isEmpty()) {
            initializedState = new VillagerPotentialState(
                    VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                    initializedState.aptitudes(),
                    state.careers(),
                    state.activeProfession(),
                    state.professionActivities(),
                    state.tradePalettes(),
                    state.marketDemand()
            );
        }
        entity.setData(POTENTIAL, initializedState);
        VillagerPotentialDiagnostics.initialization(
                entity.getUUID(),
                initializedState.schemaVersion(),
                initializedState.aptitudes().size()
        );
        VillagerPotentialLifecycleEvents.emitInitialized(
                new VillagerPotentialLifecycleEvents.Initialized(
                        entity,
                        PotentialViews.snapshot(initializedState)
                )
        );
        return initializedState;
    }

    /**
     * Potential is immutable, so retaining the complete state object is equivalent
     * to a serialize/deserialize copy and automatically includes later state fields.
     */
    static VillagerPotentialState copyVillagerIdentity(
            VillagerPotentialState state,
            IAttachmentHolder target,
            HolderLookup.Provider provider
    ) {
        return target instanceof Villager || target instanceof ZombieVillager ? state : null;
    }

    /**
     * Initializes a vanilla-bred child from its parents without replacing any
     * Potential that another breeding integration has already supplied.
     */
    public static VillagerPotentialState inherit(
            Villager firstParent,
            Villager secondParent,
            Villager child
    ) {
        Objects.requireNonNull(child, "child");
        return inherit(
                firstParent,
                secondParent,
                child,
                new Random(inheritanceSeed(worldSeed(child), child.getUUID()))
        );
    }

    static VillagerPotentialState inherit(
            Villager firstParent,
            Villager secondParent,
            Villager child,
            RandomGenerator random
    ) {
        Objects.requireNonNull(firstParent, "firstParent");
        Objects.requireNonNull(secondParent, "secondParent");
        Objects.requireNonNull(child, "child");
        Objects.requireNonNull(random, "random");

        VillagerPotentialState existingChildState = child.getData(POTENTIAL);
        if (!existingChildState.aptitudes().isEmpty()) {
            return existingChildState;
        }

        VillagerPotentialConfig config = ServerConfig.gameplayConfig();
        VillagerPotentialState inheritedState = AptitudeInheritance.inherit(
                get(firstParent),
                get(secondParent),
                VillagerProfessionIds.supportedVanillaProfessions(),
                config.aptitude(),
                config.inheritance(),
                random
        );
        child.setData(POTENTIAL, inheritedState);
        VillagerPotentialDiagnostics.inheritance(
                child.getUUID(),
                firstParent.getUUID(),
                secondParent.getUUID()
        );
        VillagerPotentialLifecycleEvents.emitInherited(
                new VillagerPotentialLifecycleEvents.Inherited(
                        child,
                        firstParent,
                        secondParent,
                        PotentialViews.snapshot(inheritedState)
                )
        );
        return inheritedState;
    }

    private static void persistAndEmit(
            Villager villager,
            VillagerPotentialState previous,
            VillagerPotentialState updated
    ) {
        if (updated == previous) {
            return;
        }
        villager.setData(POTENTIAL, updated);
        PotentialView view = PotentialViews.snapshot(updated);
        if (!previous.activeProfession().equals(updated.activeProfession())) {
            VillagerPotentialDiagnostics.profession(
                    villager.getUUID(),
                    previous.activeProfession(),
                    updated.activeProfession()
            );
            VillagerPotentialLifecycleEvents.emitProfessionChanged(
                    new VillagerPotentialLifecycleEvents.ProfessionChanged(
                            villager,
                            previous.activeProfession(),
                            updated.activeProfession(),
                            view
                    )
            );
        }
        updated.careers().forEach((profession, career) -> {
            ProfessionCareerState prior = previous.careers().get(profession);
            double priorSkill = prior == null ? 0.0 : prior.learnedSkill();
            if (Double.compare(priorSkill, career.learnedSkill()) != 0) {
                VillagerPotentialLifecycleEvents.emitSkillChanged(
                        new VillagerPotentialLifecycleEvents.SkillChanged(
                                villager,
                                profession,
                                priorSkill,
                                career.learnedSkill(),
                                view
                        )
                );
            }
            Optional<SpecializationId> priorSpecialization = prior == null
                    ? Optional.empty()
                    : prior.specialization();
            if (priorSpecialization.isEmpty() && career.specialization().isPresent()) {
                VillagerPotentialDiagnostics.specialization(
                        villager.getUUID(),
                        profession,
                        career.specialization().orElseThrow()
                );
                VillagerPotentialLifecycleEvents.emitSpecializationAssigned(
                        new VillagerPotentialLifecycleEvents.SpecializationAssigned(
                                villager,
                                profession,
                                career.specialization().orElseThrow(),
                                view
                        )
                );
            }
        });
    }

    private static PotentialView.DemandInfo demandInfo(MarketDemandState demand) {
        return new PotentialView.DemandInfo(
                demand.demandScore(),
                demand.timesPurchased(),
                demand.lastPurchaseGameTime()
        );
    }

    static VillagerPotentialState initialize(long worldSeed, UUID villagerId) {
        Random random = new Random(initializationSeed(worldSeed, villagerId));
        AptitudeGenerationConfig aptitudeConfig = ServerConfig.gameplayConfig().aptitude();
        Map<ProfessionId, Double> aptitudes = new LinkedHashMap<>();
        for (ProfessionId professionId : VillagerProfessionIds.supportedVanillaProfessions()) {
            aptitudes.put(professionId, AptitudeGenerator.generate(aptitudeConfig, random));
        }
        return new VillagerPotentialState(VillagerPotentialState.CURRENT_SCHEMA_VERSION, aptitudes);
    }

    private static long worldSeed(Entity entity) {
        return entity.level() instanceof ServerLevel serverLevel ? serverLevel.getSeed() : 0L;
    }

    private static long initializationSeed(long worldSeed, UUID villagerId) {
        return mixedSeed(worldSeed, villagerId, INITIALIZATION_SALT);
    }

    private static long inheritanceSeed(long worldSeed, UUID villagerId) {
        return mixedSeed(worldSeed, villagerId, INHERITANCE_SALT);
    }

    private static long specializationSeed(
            long worldSeed,
            UUID villagerId,
            ProfessionId professionId
    ) {
        long professionSalt = SPECIALIZATION_SALT;
        String profession = professionId.toString();
        for (int index = 0; index < profession.length(); index++) {
            professionSalt ^= profession.charAt(index);
            professionSalt *= 0x100000001B3L;
        }
        return mixedSeed(worldSeed, villagerId, professionSalt);
    }

    private static long lazyAptitudeSeed(
            long worldSeed,
            UUID villagerId,
            ProfessionId professionId
    ) {
        return mixedSeed(
                worldSeed,
                villagerId,
                professionSalt(LAZY_APTITUDE_SALT, professionId)
        );
    }

    private static long professionSalt(long baseSalt, ProfessionId professionId) {
        long professionSalt = baseSalt;
        String profession = professionId.toString();
        for (int index = 0; index < profession.length(); index++) {
            professionSalt ^= profession.charAt(index);
            professionSalt *= 0x100000001B3L;
        }
        return professionSalt;
    }

    private static long mixedSeed(long worldSeed, UUID villagerId, long salt) {
        Objects.requireNonNull(villagerId, "villagerId");
        long seed = worldSeed
                ^ villagerId.getMostSignificantBits()
                ^ Long.rotateLeft(villagerId.getLeastSignificantBits(), 32)
                ^ salt;
        seed = (seed ^ (seed >>> 30)) * 0xBF58476D1CE4E5B9L;
        seed = (seed ^ (seed >>> 27)) * 0x94D049BB133111EBL;
        return seed ^ (seed >>> 31);
    }

    private static DataResult<ProfessionId> parseProfessionId(String value) {
        try {
            return DataResult.success(ProfessionId.parse(value));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static DataResult<SpecializationId> parseSpecializationId(String value) {
        try {
            return DataResult.success(SpecializationId.parse(value));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static Optional<Long> boxed(OptionalLong value) {
        return value.isPresent() ? Optional.of(value.getAsLong()) : Optional.empty();
    }

    private static OptionalLong unboxed(Optional<Long> value) {
        return value.isPresent() ? OptionalLong.of(value.orElseThrow()) : OptionalLong.empty();
    }

    private static Map<TradeKey, MarketDemandState> marketDemandFromEntries(
            List<PersistedMarketDemand> entries
    ) {
        Map<TradeKey, MarketDemandState> demand = new LinkedHashMap<>();
        entries.forEach(entry -> demand.put(entry.trade(), entry.demand()));
        return demand;
    }

    private static List<PersistedMarketDemand> marketDemandToEntries(
            Map<TradeKey, MarketDemandState> demand
    ) {
        return demand.entrySet().stream()
                .map(entry -> new PersistedMarketDemand(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static DataResult<VillagerPotentialState> migrate(
            int schemaVersion,
            Map<ProfessionId, Double> aptitudes,
            Map<ProfessionId, ProfessionCareerState> careers,
            Optional<ProfessionId> activeProfession,
            Map<ProfessionId, ProfessionActivityState> professionActivities,
            Map<ProfessionId, TradePaletteState> tradePalettes,
            Map<ProfessionId, Map<TradeKey, MarketDemandState>> marketDemand
    ) {
        try {
            VillagerPotentialState migrated = VillagerPotentialState.migrate(
                    schemaVersion,
                    aptitudes,
                    careers,
                    activeProfession,
                    professionActivities,
                    tradePalettes,
                    marketDemand
            );
            if (schemaVersion != migrated.schemaVersion()) {
                VillagerPotentialDiagnostics.migration(schemaVersion, migrated.schemaVersion());
            }
            return DataResult.success(migrated);
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private record PersistedState(
            int schemaVersion,
            Map<ProfessionId, Double> aptitudes,
            Map<ProfessionId, ProfessionCareerState> careers,
            Optional<ProfessionId> activeProfession,
            Map<ProfessionId, ProfessionActivityState> professionActivities,
            Map<ProfessionId, TradePaletteState> tradePalettes,
            Map<ProfessionId, Map<TradeKey, MarketDemandState>> marketDemand
    ) {
    }

    private record PersistedMarketDemand(TradeKey trade, MarketDemandState demand) {
    }

    private record PersistedTradeHistory(TradeKey trade, TradeHistory history) {
    }

    private record PersistedTradePalette(
            List<TradeKey> activeTrades,
            List<TradeKey> selectionHistory,
            List<PersistedTradeHistory> offerHistory
    ) {
        private TradePaletteState toState() {
            if (!offerHistory.isEmpty()) {
                Map<TradeKey, TradeHistory> histories = new LinkedHashMap<>();
                for (PersistedTradeHistory entry : offerHistory) {
                    histories.put(entry.trade(), entry.history());
                }
                return new TradePaletteState(activeTrades, histories);
            }
            return new TradePaletteState(activeTrades, selectionHistory);
        }

        private static PersistedTradePalette fromState(TradePaletteState state) {
            List<PersistedTradeHistory> histories = state.offerHistory().entrySet().stream()
                    .map(entry -> new PersistedTradeHistory(entry.getKey(), entry.getValue()))
                    .toList();
            return new PersistedTradePalette(state.activeTrades(), List.of(), histories);
        }
    }

    private static final class ProfessionProgressBatch {
        private final ProfessionId profession;
        private long elapsedProfessionTime;
        private long lastObservedGameTime;
        private int vanillaLevel;

        private ProfessionProgressBatch(
                ProfessionId profession,
                long elapsedProfessionTime,
                long lastObservedGameTime,
                int vanillaLevel
        ) {
            this.profession = profession;
            this.elapsedProfessionTime = elapsedProfessionTime;
            this.lastObservedGameTime = lastObservedGameTime;
            this.vanillaLevel = vanillaLevel;
        }

        private ProfessionId profession() {
            return profession;
        }

        private long elapsedProfessionTime() {
            return elapsedProfessionTime;
        }

        private long lastObservedGameTime() {
            return lastObservedGameTime;
        }

        private int vanillaLevel() {
            return vanillaLevel;
        }

        private void observeVanillaLevel(int level) {
            vanillaLevel = level;
        }

        private void observeGameTime(long gameTime) {
            lastObservedGameTime = Math.max(lastObservedGameTime, gameTime);
        }

        private void addElapsedTick() {
            elapsedProfessionTime++;
        }

        private void clearElapsedTime() {
            elapsedProfessionTime = 0L;
        }
    }
}
