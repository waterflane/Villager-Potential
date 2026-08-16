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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToDoubleFunction;

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

    /**
     * @return {@code true} when weighted selection handled the call, or
     * {@code false} when the original vanilla method must run unchanged
     */
    public static boolean tryAddOffers(
            Villager villager,
            MerchantOffers offers,
            VillagerTrades.ItemListing[] candidates,
            int requestedOfferCount
    ) {
        Objects.requireNonNull(villager, "villager");
        VillagerProfession profession = villager.getVillagerData().getProfession();
        ProfessionId professionId = VillagerProfessionIds.fromMinecraft(profession);
        Optional<ProfessionSpecializationDefinition> professionDefinition =
                SpecializationDefinitionManager.INSTANCE.definitionFor(professionId);
        var potential = VillagerPotentialAttachments.get(villager);
        TradePaletteRerollStrategy strategy = Config.tradePaletteRerollStrategy();
        List<TradeKey> learnedTrades = potential
                .tradePaletteFor(professionId)
                .map(palette -> palette.activeTrades())
                .orElse(List.of());
        if (strategy == TradePaletteRerollStrategy.PERSISTENT
                && offers.isEmpty()
                && !learnedTrades.isEmpty()) {
            restorePersistentOffers(
                    villager,
                    offers,
                    learnedTrades,
                    profession,
                    villager.getVillagerData().getLevel(),
                    villager.getRandom()
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
        boolean usesMemory = strategy == TradePaletteRerollStrategy.WEIGHTED_MEMORY
                || strategy == TradePaletteRerollStrategy.EXHAUST
                || strategy == TradePaletteRerollStrategy.CYCLIC;
        if (modifiers.isEmpty() && (!usesMemory || offerHistory.isEmpty())) {
            return false;
        }

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
                Config.specializationBiasConfig(),
                offerHistory,
                Config.seenTradeWeightMultiplier(),
                strategy,
                career.map(value -> value.accumulatedProfessionTime()).orElse(0L),
                Config.tradeMemoryRecoveryConfig(),
                villager.getRandom()
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
        int offersAdded = 0;
        while (offersAdded < requestedOfferCount && !remaining.isEmpty()) {
            long cycleFloor = strategy == TradePaletteRerollStrategy.CYCLIC
                    ? remaining.stream()
                    .mapToLong(candidate -> TradeMemoryRecovery.effectiveCyclicCount(
                            offerHistory.get(candidate.key()),
                            professionTime,
                            recoveryConfig,
                            Config.isRareTradeProtected(candidate.key())
                    ))
                    .min()
                    .orElse(0L)
                    : 0L;
            int selectedIndex = weightedIndex(
                    remaining,
                    candidate -> weight(
                            candidate,
                            profession,
                            level,
                            specialization,
                            skill,
                            biasConfig,
                            offerHistory,
                            seenTradeWeightMultiplier,
                            strategy,
                            cycleFloor,
                            professionTime,
                            recoveryConfig,
                            resetCycle
                    ),
                    random
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
            RandomSource random
    ) {
        List<VillagerTrades.ItemListing> remaining = new ArrayList<>(Arrays.asList(candidates));
        int offersAdded = 0;
        while (offersAdded < requestedOfferCount && !remaining.isEmpty()) {
            int selectedIndex = weightedIndex(
                    remaining,
                    candidate -> specializationWeight(
                            candidate,
                            profession,
                            level,
                            specialization,
                            skill,
                            biasConfig
                    ),
                    random
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

    private static <T> int weightedIndex(
            List<T> candidates,
            ToDoubleFunction<T> weight,
            RandomSource random
    ) {
        double maximumWeight = 0.0;
        for (T candidate : candidates) {
            maximumWeight = Math.max(maximumWeight, weight.applyAsDouble(candidate));
        }
        if (maximumWeight == 0.0) {
            return -1;
        }

        double normalizedTotal = 0.0;
        for (T candidate : candidates) {
            normalizedTotal += weight.applyAsDouble(candidate) / maximumWeight;
        }

        double target = random.nextDouble() * normalizedTotal;
        int lastPositiveIndex = -1;
        double cumulativeWeight = 0.0;
        for (int index = 0; index < candidates.size(); index++) {
            double normalizedWeight = weight.applyAsDouble(candidates.get(index)) / maximumWeight;
            if (normalizedWeight == 0.0) {
                continue;
            }
            lastPositiveIndex = index;
            cumulativeWeight += normalizedWeight;
            if (target < cumulativeWeight) {
                return index;
            }
        }

        // Protect against the final addition rounding slightly below the total.
        return lastPositiveIndex;
    }

    private static double weight(
            GeneratedCandidate candidate,
            VillagerProfession profession,
            int level,
            Optional<SpecializationDefinition> specialization,
            double skill,
            SpecializationBiasConfig biasConfig,
            Map<TradeKey, TradeHistory> offerHistory,
            double seenTradeWeightMultiplier,
            TradePaletteRerollStrategy strategy,
            long cycleFloor,
            long professionTime,
            TradeMemoryRecoveryConfig recoveryConfig,
            boolean resetCycle
    ) {
        double specializationWeight = specializationWeight(
                candidate.listing(),
                profession,
                level,
                specialization,
                skill,
                biasConfig
        );
        return TradeMemoryRecovery.candidateWeight(
                strategy,
                specializationWeight,
                offerHistory.get(candidate.key()),
                professionTime,
                seenTradeWeightMultiplier,
                recoveryConfig,
                Config.isRareTradeProtected(candidate.key()),
                cycleFloor,
                resetCycle
        );
    }

    private static double specializationWeight(
            VillagerTrades.ItemListing candidate,
            VillagerProfession profession,
            int level,
            Optional<SpecializationDefinition> specialization,
            double skill,
            SpecializationBiasConfig biasConfig
    ) {
        return specialization
                .map(definition -> biasConfig.weightModifier(
                        definition.weightModifierFor(
                                VanillaTradeClassifications.classify(
                                        profession,
                                        level,
                                        candidate
                                )
                        ),
                        skill
                ))
                .orElse(1.0);
    }

    static double tradeMemoryWeight(
            double baselineWeight,
            TradeKey candidate,
            Map<TradeKey, TradeHistory> offerHistory,
            double seenTradeWeightMultiplier
    ) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(offerHistory, "offerHistory");
        TradeHistory history = offerHistory.get(candidate);
        return history != null && history.timesSeen() > 0L
                ? baselineWeight * seenTradeWeightMultiplier
                : baselineWeight;
    }

    private static long latestSeenTime(Map<TradeKey, TradeHistory> offerHistory) {
        return offerHistory.values().stream()
                .filter(history -> history.lastSeen().isPresent())
                .mapToLong(history -> history.lastSeen().getAsLong())
                .max()
                .orElse(0L);
    }

    private static void restorePersistentOffers(
            Villager villager,
            MerchantOffers offers,
            List<TradeKey> learnedTrades,
            VillagerProfession profession,
            int maximumLevel,
            RandomSource random
    ) {
        Int2ObjectMap<VillagerTrades.ItemListing[]> pools = tradePools(villager, profession);
        List<VillagerTrades.ItemListing[]> unlockedPools = new ArrayList<>();
        for (int level = 1; level <= maximumLevel; level++) {
            VillagerTrades.ItemListing[] pool = pools == null ? null : pools.get(level);
            if (pool != null) {
                unlockedPools.add(pool);
            }
        }
        restorePersistentOffers(villager, offers, learnedTrades, unlockedPools, random);
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
        Set<ListingSlot> consumed = new HashSet<>();
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
            }
        }
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
                    TradeKey generated = MerchantOfferTradeKeys.from(offer);
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
        return offer == null
                ? Optional.empty()
                : Optional.of(new GeneratedCandidate(
                        listing,
                        offer,
                        MerchantOfferTradeKeys.from(offer)
                ));
    }

    private record GeneratedCandidate(
            VillagerTrades.ItemListing listing,
            MerchantOffer offer,
            TradeKey key
    ) {
    }

    private record ListingSlot(int pool, int listing) {
    }

    private record MatchedOffer(ListingSlot slot, MerchantOffer offer) {
    }
}
