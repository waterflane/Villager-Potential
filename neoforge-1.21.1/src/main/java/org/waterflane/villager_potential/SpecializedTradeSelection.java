package org.waterflane.villager_potential;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.ProfessionSpecializationDefinition;
import org.waterflane.villager_potential.core.SpecializationBiasConfig;
import org.waterflane.villager_potential.core.SpecializationDefinition;
import org.waterflane.villager_potential.core.SpecializationId;
import org.waterflane.villager_potential.core.TradeHistory;
import org.waterflane.villager_potential.core.TradeKey;
import org.waterflane.villager_potential.core.TradeMemoryRecovery;
import org.waterflane.villager_potential.core.TradeMemoryRecoveryConfig;
import org.waterflane.villager_potential.core.TradePaletteRerollStrategy;
import org.waterflane.villager_potential.core.TradeSelectionResolver;
import org.waterflane.villager_potential.core.TradeCategoryId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Applies specialization and remembered-offer weights to the candidate array
 * selected by vanilla.
 *
 * <p>The input array is never mutated. Selection remains sampling without
 * replacement, and a listing that returns {@code null} is discarded without
 * consuming one of the requested offer slots, matching vanilla semantics.
 * When memory is present, listings are materialized once before selection so
 * randomized output such as a specific enchanted book is both weighted and
 * returned under the same logical key.</p>
 */
public final class SpecializedTradeSelection {
    private static final int PERSISTENT_MATCH_ATTEMPTS = 4096;
    private static final TradeMemoryRecoveryConfig DEFAULT_MEMORY_RECOVERY =
            new TradeMemoryRecoveryConfig(24_000L, 0.01, 24_000L, 24_000L, 0L);

    private SpecializedTradeSelection() {
    }

    /** @return {@code true} after this resolver has handled the generation call */
    public static boolean tryAddOffers(
            Villager villager,
            MerchantOffers offers,
            VillagerTrades.ItemListing[] candidates,
            int requestedOfferCount
    ) {
        Objects.requireNonNull(villager, "villager");
        VillagerProfession profession = villager.getVillagerData().getProfession();
        Optional<ProfessionId> portableProfession =
                VillagerProfessionIds.tryFromMinecraft(profession);
        if (portableProfession.isEmpty()) {
            return false;
        }
        ProfessionId professionId = portableProfession.orElseThrow();
        Optional<ProfessionSpecializationDefinition> professionDefinition =
                SpecializationDefinitionManager.INSTANCE.definitionFor(professionId);
        var potential = VillagerPotentialAttachments.get(villager);
        TradePaletteRerollStrategy strategy = Config.tradePaletteRerollStrategy();
        int firstGeneratedIndex = offers.size();
        List<TradeKey> learnedTrades = potential
                .tradePaletteFor(professionId)
                .map(palette -> palette.activeTrades())
                .orElse(List.of());
        if (strategy == TradePaletteRerollStrategy.PERSISTENT
                && offers.isEmpty()
                && !learnedTrades.isEmpty()) {
            boolean restored = tryRestorePersistentOffers(
                    villager,
                    offers,
                    learnedTrades,
                    profession,
                    villager.getVillagerData().getLevel(),
                    candidates,
                    villager.getRandom()
            );
            if (!restored) {
                // Unknown or removed content stays under the originating trade
                // system. The mixin's RETURN hook may still observe stable keys.
                VillagerPotentialDiagnostics.tradeDecision(
                        villager.getUUID(),
                        professionId,
                        strategy,
                        "restoration-yielded-to-origin"
                );
                return false;
            }
            VillagerPotentialDiagnostics.tradeDecision(
                    villager.getUUID(),
                    professionId,
                    strategy,
                    "restored=" + (offers.size() - firstGeneratedIndex)
            );
            VillagerPotentialTradeEvents.emitTradeProcessing(
                    new VillagerPotentialTradeEvents.TradeProcessing(
                            villager,
                            professionId,
                            strategy,
                            VillagerPotentialTradeEvents.ProcessingKind.PERSISTENT_RESTORATION,
                            offers.subList(firstGeneratedIndex, offers.size()).stream()
                                    .map(MerchantOfferTradeKeys::from)
                                    .toList()
                    )
            );
            return true;
        }
        Optional<SpecializationId> specializationId = potential.specializationFor(professionId);
        Optional<SpecializationDefinition> modifiers = selectionModifiers(
                professionDefinition,
                specializationId
        );
        Map<TradeKey, TradeHistory> offerHistory = potential
                .tradePaletteFor(professionId)
                .map(palette -> palette.offerHistory())
                .orElse(Map.of());
        var career = potential.careerFor(professionId);
        addWeightedOffers(
                villager,
                offers,
                candidates,
                requestedOfferCount,
                profession,
                villager.getVillagerData().getLevel(),
                modifiers,
                career.map(value -> value.learnedSkill()).orElse(0.0),
                Config.specializationBiasConfig(professionId),
                offerHistory,
                Config.seenTradeWeightMultiplier(),
                strategy,
                career.map(value -> value.accumulatedProfessionTime()).orElse(0L),
                Config.tradeMemoryRecoveryConfig(),
                villager.getRandom()
        );
        VillagerPotentialTradeEvents.ProcessingKind processingKind =
                firstGeneratedIndex == 0
                        && !offerHistory.isEmpty()
                        && strategy != TradePaletteRerollStrategy.PERSISTENT
                        && strategy != TradePaletteRerollStrategy.VANILLA
                        ? VillagerPotentialTradeEvents.ProcessingKind.REROLL
                        : VillagerPotentialTradeEvents.ProcessingKind.INITIAL_OR_NEW_LEVEL_GENERATION;
        VillagerPotentialTradeEvents.emitTradeProcessing(
                new VillagerPotentialTradeEvents.TradeProcessing(
                        villager,
                        professionId,
                        strategy,
                        processingKind,
                        offers.subList(firstGeneratedIndex, offers.size()).stream()
                                .map(MerchantOfferTradeKeys::from)
                                .toList()
                )
        );
        VillagerPotentialDiagnostics.tradeDecision(
                villager.getUUID(),
                professionId,
                strategy,
                processingKind + " generated=" + (offers.size() - firstGeneratedIndex)
        );
        return true;
    }

    static Optional<SpecializationDefinition> selectionModifiers(
            Optional<ProfessionSpecializationDefinition> professionDefinition,
            Optional<SpecializationId> specializationId
    ) {
        Objects.requireNonNull(professionDefinition, "professionDefinition");
        Objects.requireNonNull(specializationId, "specializationId");
        if (professionDefinition.isEmpty() || specializationId.isEmpty()) {
            return Optional.empty();
        }

        ProfessionSpecializationDefinition definition = professionDefinition.orElseThrow();
        SpecializationId selected = specializationId.orElseThrow();
        return definition.generalSpecialization().equals(selected)
                ? Optional.empty()
                : definition.specialization(selected);
    }

    static void addWeightedOffers(
            Villager villager,
            MerchantOffers offers,
            VillagerTrades.ItemListing[] candidates,
            int requestedOfferCount,
            VillagerProfession profession,
            int level,
            SpecializationDefinition specialization,
            double skill,
            SpecializationBiasConfig biasConfig,
            RandomSource random
    ) {
        addWeightedOffers(
                villager,
                offers,
                candidates,
                requestedOfferCount,
                profession,
                level,
                Optional.of(specialization),
                skill,
                biasConfig,
                Map.of(),
                1.0,
                TradePaletteRerollStrategy.VANILLA,
                random
        );
    }

    static void addWeightedOffers(
            Villager villager,
            MerchantOffers offers,
            VillagerTrades.ItemListing[] candidates,
            int requestedOfferCount,
            VillagerProfession profession,
            int level,
            Optional<SpecializationDefinition> specialization,
            double skill,
            SpecializationBiasConfig biasConfig,
            Map<TradeKey, TradeHistory> offerHistory,
            double seenTradeWeightMultiplier,
            RandomSource random
    ) {
        addWeightedOffers(
                villager,
                offers,
                candidates,
                requestedOfferCount,
                profession,
                level,
                specialization,
                skill,
                biasConfig,
                offerHistory,
                seenTradeWeightMultiplier,
                TradePaletteRerollStrategy.WEIGHTED_MEMORY,
                random
        );
    }

    static void addWeightedOffers(
            Villager villager,
            MerchantOffers offers,
            VillagerTrades.ItemListing[] candidates,
            int requestedOfferCount,
            VillagerProfession profession,
            int level,
            Optional<SpecializationDefinition> specialization,
            double skill,
            SpecializationBiasConfig biasConfig,
            Map<TradeKey, TradeHistory> offerHistory,
            double seenTradeWeightMultiplier,
            TradePaletteRerollStrategy strategy,
            RandomSource random
    ) {
        addWeightedOffers(
                villager,
                offers,
                candidates,
                requestedOfferCount,
                profession,
                level,
                specialization,
                skill,
                biasConfig,
                offerHistory,
                seenTradeWeightMultiplier,
                strategy,
                latestSeenTime(offerHistory),
                DEFAULT_MEMORY_RECOVERY,
                random
        );
    }

    static void addWeightedOffers(
            Villager villager,
            MerchantOffers offers,
            VillagerTrades.ItemListing[] candidates,
            int requestedOfferCount,
            VillagerProfession profession,
            int level,
            Optional<SpecializationDefinition> specialization,
            double skill,
            SpecializationBiasConfig biasConfig,
            Map<TradeKey, TradeHistory> offerHistory,
            double seenTradeWeightMultiplier,
            TradePaletteRerollStrategy strategy,
            long professionTime,
            TradeMemoryRecoveryConfig recoveryConfig,
            RandomSource random
    ) {
        Objects.requireNonNull(villager, "villager");
        Objects.requireNonNull(offers, "offers");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(profession, "profession");
        Objects.requireNonNull(specialization, "specialization");
        Objects.requireNonNull(biasConfig, "biasConfig");
        Objects.requireNonNull(offerHistory, "offerHistory");
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(recoveryConfig, "recoveryConfig");
        Objects.requireNonNull(random, "random");
        if (!Double.isFinite(seenTradeWeightMultiplier)
                || seenTradeWeightMultiplier < 0.0
                || seenTradeWeightMultiplier > 1.0) {
            throw new IllegalArgumentException(
                    "seenTradeWeightMultiplier must be finite and between zero and one"
            );
        }

        if (offerHistory.isEmpty()
                || strategy == TradePaletteRerollStrategy.VANILLA
                || strategy == TradePaletteRerollStrategy.PERSISTENT) {
            addBaselineWeightedOffers(
                    villager,
                    offers,
                    candidates,
                    requestedOfferCount,
                    profession,
                    level,
                    specialization,
                    skill,
                    biasConfig,
                    strategy,
                    professionTime,
                    seenTradeWeightMultiplier,
                    recoveryConfig,
                    random
            );
            return;
        }

        List<GeneratedCandidate> remaining = Arrays.stream(candidates)
                .map(candidate -> generateCandidate(candidate, villager, random))
                .flatMap(Optional::stream)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        boolean resetCycle = strategy == TradePaletteRerollStrategy.CYCLIC
                && TradeMemoryRecovery.shouldResetCycle(
                offerHistory.values(),
                professionTime,
                recoveryConfig
        );
        TradeSelectionResolver.SelectionRandom selectionRandom = selectionRandom(random);
        int offersAdded = 0;
        while (offersAdded < requestedOfferCount && !remaining.isEmpty()) {
            long cycleFloor = strategy == TradePaletteRerollStrategy.CYCLIC
                    ? remaining.stream()
                    .filter(GeneratedCandidate::stableIdentity)
                    .mapToLong(candidate -> TradeMemoryRecovery.effectiveCyclicCount(
                            offerHistory.get(candidate.key()),
                            professionTime,
                            recoveryConfig,
                            Config.isRareTradeProtected(candidate.key())
                    ))
                    .min()
                    .orElse(0L)
                    : 0L;
            TradeSelectionResolver.Rules rules = rules(
                    skill,
                    biasConfig,
                    strategy,
                    professionTime,
                    seenTradeWeightMultiplier,
                    recoveryConfig,
                    cycleFloor,
                    resetCycle
            );
            int selectedIndex = TradeSelectionResolver.selectIndex(
                    remaining.stream()
                            .map(candidate -> descriptor(
                                    villager,
                                    candidate.listing(),
                                    candidate.key(),
                                    profession,
                                    level,
                                    specialization,
                                    offerHistory,
                                    strategy,
                                    candidate.stableIdentity()
                            ))
                            .toList(),
                    rules,
                    selectionRandom
            );
            if (selectedIndex < 0) {
                break;
            }

            GeneratedCandidate selected = remaining.remove(selectedIndex);
            offers.add(selected.offer());
            offersAdded++;
        }
    }

    private static void addBaselineWeightedOffers(
            Villager villager,
            MerchantOffers offers,
            VillagerTrades.ItemListing[] candidates,
            int requestedOfferCount,
            VillagerProfession profession,
            int level,
            Optional<SpecializationDefinition> specialization,
            double skill,
            SpecializationBiasConfig biasConfig,
            TradePaletteRerollStrategy strategy,
            long professionTime,
            double seenTradeWeightMultiplier,
            TradeMemoryRecoveryConfig recoveryConfig,
            RandomSource random
    ) {
        if (VillagerPotentialTradeEvents.hasCandidateWeightModifiers()
                || ServerConfig.detailedWeightLoggingEnabled()) {
            addMaterializedBaselineOffers(
                    villager,
                    offers,
                    candidates,
                    requestedOfferCount,
                    profession,
                    level,
                    specialization,
                    skill,
                    biasConfig,
                    strategy,
                    professionTime,
                    seenTradeWeightMultiplier,
                    recoveryConfig,
                    random
            );
            return;
        }
        List<VillagerTrades.ItemListing> remaining = new ArrayList<>(Arrays.asList(candidates));
        TradeSelectionResolver.Rules rules = rules(
                skill,
                biasConfig,
                strategy,
                professionTime,
                seenTradeWeightMultiplier,
                recoveryConfig,
                0L,
                false
        );
        TradeSelectionResolver.SelectionRandom selectionRandom = selectionRandom(random);
        int offersAdded = 0;
        while (offersAdded < requestedOfferCount && !remaining.isEmpty()) {
            int selectedIndex = TradeSelectionResolver.selectIndex(
                    remaining.stream()
                            .map(candidate -> descriptor(
                                    villager,
                                    candidate,
                                    null,
                                    profession,
                                    level,
                                    specialization,
                                    Map.of(),
                                    strategy,
                                    false
                            ))
                            .toList(),
                    rules,
                    selectionRandom
            );
            if (selectedIndex < 0) {
                break;
            }

            MerchantOffer offer = remaining.remove(selectedIndex).getOffer(villager, random);
            if (offer != null) {
                offers.add(offer);
                offersAdded++;
            }
        }
    }

    /** Materializes only when an integration needs a portable TradeKey. */
    private static void addMaterializedBaselineOffers(
            Villager villager,
            MerchantOffers offers,
            VillagerTrades.ItemListing[] candidates,
            int requestedOfferCount,
            VillagerProfession profession,
            int level,
            Optional<SpecializationDefinition> specialization,
            double skill,
            SpecializationBiasConfig biasConfig,
            TradePaletteRerollStrategy strategy,
            long professionTime,
            double seenTradeWeightMultiplier,
            TradeMemoryRecoveryConfig recoveryConfig,
            RandomSource random
    ) {
        List<GeneratedCandidate> remaining = Arrays.stream(candidates)
                .map(candidate -> generateCandidate(candidate, villager, random))
                .flatMap(Optional::stream)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        TradeSelectionResolver.Rules rules = rules(
                skill,
                biasConfig,
                strategy,
                professionTime,
                seenTradeWeightMultiplier,
                recoveryConfig,
                0L,
                false
        );
        TradeSelectionResolver.SelectionRandom selectionRandom = selectionRandom(random);
        int offersAdded = 0;
        while (offersAdded < requestedOfferCount && !remaining.isEmpty()) {
            int selectedIndex = TradeSelectionResolver.selectIndex(
                    remaining.stream()
                            .map(candidate -> descriptor(
                                    villager,
                                    candidate.listing(),
                                    candidate.key(),
                                    profession,
                                    level,
                                    specialization,
                                    Map.of(),
                                    strategy,
                                    candidate.stableIdentity()
                            ))
                            .toList(),
                    rules,
                    selectionRandom
            );
            if (selectedIndex < 0) {
                break;
            }
            offers.add(remaining.remove(selectedIndex).offer());
            offersAdded++;
        }
    }

    private static TradeSelectionResolver.Candidate descriptor(
            Villager villager,
            VillagerTrades.ItemListing candidate,
            TradeKey key,
            VillagerProfession profession,
            int level,
            Optional<SpecializationDefinition> specialization,
            Map<TradeKey, TradeHistory> offerHistory,
            TradePaletteRerollStrategy strategy,
            boolean stableIdentity
    ) {
        boolean hasIntegrationModifiers =
                VillagerPotentialTradeEvents.hasCandidateWeightModifiers();
        boolean logWeights = ServerConfig.detailedWeightLoggingEnabled();
        if (!hasIntegrationModifiers && !logWeights) {
            return new TradeSelectionResolver.Candidate(
                    1.0,
                    specializationModifier(candidate, profession, level, specialization),
                    1.0,
                    key == null || !stableIdentity ? null : offerHistory.get(key),
                    key != null && stableIdentity && Config.isRareTradeProtected(key)
            );
        }
        ProfessionId professionId = VillagerProfessionIds.fromMinecraft(profession);
        TradeCategoryId category = VanillaTradeClassifications.classify(
                profession,
                level,
                candidate
        );
        var weightContext = new VillagerPotentialTradeEvents.CandidateWeight(
                villager,
                professionId,
                level,
                Objects.requireNonNull(key, "key"),
                category,
                strategy
        );
        return new TradeSelectionResolver.Candidate(
                1.0,
                specialization
                        .map(definition -> definition.weightModifierFor(category))
                        .orElse(1.0),
                1.0,
                key == null || !stableIdentity ? null : offerHistory.get(key),
                key != null && stableIdentity && Config.isRareTradeProtected(key),
                weight -> {
                    double modified = hasIntegrationModifiers
                            ? VillagerPotentialTradeEvents.modifyCandidateWeight(
                            weightContext,
                            weight
                    )
                            : weight;
                    return logWeights
                            ? VillagerPotentialDiagnostics.weight(
                            villager.getUUID(),
                            professionId,
                            key,
                            modified
                    )
                            : modified;
                }
        );
    }

    private static double specializationModifier(
            VillagerTrades.ItemListing candidate,
            VillagerProfession profession,
            int level,
            Optional<SpecializationDefinition> specialization
    ) {
        return specialization
                .map(definition -> definition.weightModifierFor(
                        VanillaTradeClassifications.classify(profession, level, candidate)
                ))
                .orElse(1.0);
    }

    private static TradeSelectionResolver.Rules rules(
            double skill,
            SpecializationBiasConfig biasConfig,
            TradePaletteRerollStrategy strategy,
            long professionTime,
            double seenTradeWeightMultiplier,
            TradeMemoryRecoveryConfig recoveryConfig,
            long cycleFloor,
            boolean resetCycle
    ) {
        return new TradeSelectionResolver.Rules(
                skill,
                biasConfig,
                strategy,
                professionTime,
                seenTradeWeightMultiplier,
                recoveryConfig,
                cycleFloor,
                resetCycle
        );
    }

    private static TradeSelectionResolver.SelectionRandom selectionRandom(RandomSource random) {
        return new TradeSelectionResolver.SelectionRandom() {
            @Override
            public int nextInt(int bound) {
                return random.nextInt(bound);
            }

            @Override
            public double nextDouble() {
                return random.nextDouble();
            }
        };
    }

    private static long latestSeenTime(Map<TradeKey, TradeHistory> offerHistory) {
        return offerHistory.values().stream()
                .filter(history -> history.lastSeen().isPresent())
                .mapToLong(history -> history.lastSeen().getAsLong())
                .max()
                .orElse(0L);
    }

    private static boolean tryRestorePersistentOffers(
            Villager villager,
            MerchantOffers offers,
            List<TradeKey> learnedTrades,
            VillagerProfession profession,
            int maximumLevel,
            VillagerTrades.ItemListing[] currentCandidates,
            RandomSource random
    ) {
        if (learnedTrades.stream().anyMatch(trade -> !MerchantOfferTradeKeys.isStable(trade))) {
            return false;
        }
        Int2ObjectMap<VillagerTrades.ItemListing[]> pools = tradePools(villager, profession);
        List<VillagerTrades.ItemListing[]> unlockedPools = new ArrayList<>();
        boolean currentCandidatesAreRegisteredPool = false;
        if (pools != null) {
            for (int level = 1; level <= maximumLevel; level++) {
                if (pools.get(level) == currentCandidates) {
                    currentCandidatesAreRegisteredPool = true;
                    break;
                }
            }
        }
        if (pools == null || !currentCandidatesAreRegisteredPool) {
            // A foreign system supplied its own candidate array. Never replace
            // that system with the built-in/static profession pool.
            unlockedPools.add(currentCandidates);
        } else {
            for (int level = 1; level <= maximumLevel; level++) {
                VillagerTrades.ItemListing[] pool = pools.get(level);
                if (pool != null) {
                    unlockedPools.add(pool);
                }
            }
        }
        int firstRestoredIndex = offers.size();
        int restored = restorePersistentOffersInternal(
                villager,
                offers,
                learnedTrades,
                unlockedPools,
                random
        );
        if (restored == learnedTrades.size()) {
            return true;
        }
        offers.subList(firstRestoredIndex, offers.size()).clear();
        return false;
    }

    static void restorePersistentOffers(
            Villager villager,
            MerchantOffers offers,
            List<TradeKey> learnedTrades,
            List<VillagerTrades.ItemListing[]> unlockedPools,
            RandomSource random
    ) {
        Objects.requireNonNull(villager, "villager");
        Objects.requireNonNull(offers, "offers");
        Objects.requireNonNull(learnedTrades, "learnedTrades");
        Objects.requireNonNull(unlockedPools, "unlockedPools");
        Objects.requireNonNull(random, "random");
        restorePersistentOffersInternal(villager, offers, learnedTrades, unlockedPools, random);
    }

    private static int restorePersistentOffersInternal(
            Villager villager,
            MerchantOffers offers,
            List<TradeKey> learnedTrades,
            List<VillagerTrades.ItemListing[]> unlockedPools,
            RandomSource random
    ) {
        Set<ListingSlot> consumed = new HashSet<>();
        int restoredCount = 0;
        for (TradeKey learnedTrade : learnedTrades) {
            Optional<MatchedOffer> match = findPersistentOffer(
                    villager,
                    learnedTrade,
                    unlockedPools,
                    consumed,
                    random
            );
            if (match.isPresent()) {
                MatchedOffer restored = match.orElseThrow();
                consumed.add(restored.slot());
                offers.add(restored.offer());
                restoredCount++;
            }
        }
        return restoredCount;
    }

    private static Optional<MatchedOffer> findPersistentOffer(
            Villager villager,
            TradeKey learnedTrade,
            List<VillagerTrades.ItemListing[]> unlockedPools,
            Set<ListingSlot> consumed,
            RandomSource random
    ) {
        for (int poolIndex = 0; poolIndex < unlockedPools.size(); poolIndex++) {
            VillagerTrades.ItemListing[] pool = unlockedPools.get(poolIndex);
            for (int listingIndex = 0; listingIndex < pool.length; listingIndex++) {
                ListingSlot slot = new ListingSlot(poolIndex, listingIndex);
                if (consumed.contains(slot)) {
                    continue;
                }
                for (int attempt = 0; attempt < PERSISTENT_MATCH_ATTEMPTS; attempt++) {
                    MerchantOffer offer = pool[listingIndex].getOffer(villager, random);
                    if (offer == null) {
                        continue;
                    }
                    MerchantOfferTradeKeys.Identity identity = MerchantOfferTradeKeys.identify(offer);
                    if (!identity.stable()) {
                        break;
                    }
                    TradeKey generated = identity.key();
                    if (generated.equals(learnedTrade)) {
                        return Optional.of(new MatchedOffer(slot, offer));
                    }
                    if (attempt == 0 && !hasSameShape(generated, learnedTrade)) {
                        break;
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean hasSameShape(TradeKey generated, TradeKey learned) {
        if (!(generated instanceof TradeKey.Offer generatedOffer)
                || !(learned instanceof TradeKey.Offer learnedOffer)) {
            return generated.getClass().equals(learned.getClass());
        }
        return generatedOffer.costA().itemId().equals(learnedOffer.costA().itemId())
                && generatedOffer.result().itemId().equals(learnedOffer.result().itemId())
                && generatedOffer.costB().map(TradeKey.Item::itemId)
                .equals(learnedOffer.costB().map(TradeKey.Item::itemId));
    }

    private static Int2ObjectMap<VillagerTrades.ItemListing[]> tradePools(
            Villager villager,
            VillagerProfession profession
    ) {
        if (villager.level().enabledFeatures().contains(FeatureFlags.TRADE_REBALANCE)) {
            Int2ObjectMap<VillagerTrades.ItemListing[]> experimental =
                    VillagerTrades.EXPERIMENTAL_TRADES.get(profession);
            if (experimental != null) {
                return experimental;
            }
        }
        return VillagerTrades.TRADES.get(profession);
    }

    private static Optional<GeneratedCandidate> generateCandidate(
            VillagerTrades.ItemListing listing,
            Villager villager,
            RandomSource random
    ) {
        MerchantOffer offer = listing.getOffer(villager, random);
        MerchantOfferTradeKeys.Identity identity = offer == null
                ? null
                : MerchantOfferTradeKeys.identify(offer);
        return offer == null
                ? Optional.empty()
                : Optional.of(new GeneratedCandidate(
                        listing,
                        offer,
                        identity.key(),
                        identity.stable()
                ));
    }

    private record GeneratedCandidate(
            VillagerTrades.ItemListing listing,
            MerchantOffer offer,
            TradeKey key,
            boolean stableIdentity
    ) {
    }

    private record ListingSlot(int pool, int listing) {
    }

    private record MatchedOffer(ListingSlot slot, MerchantOffer offer) {
    }
}
