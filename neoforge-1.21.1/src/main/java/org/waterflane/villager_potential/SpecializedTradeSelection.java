package org.waterflane.villager_potential;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.ProfessionSpecializationDefinition;
import org.waterflane.villager_potential.core.SpecializationBiasConfig;
import org.waterflane.villager_potential.core.SpecializationDefinition;
import org.waterflane.villager_potential.core.SpecializationId;
import org.waterflane.villager_potential.core.TradeHistory;
import org.waterflane.villager_potential.core.TradeKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
        Optional<SpecializationId> specializationId = potential.specializationFor(professionId);
        Optional<SpecializationDefinition> modifiers = selectionModifiers(
                professionDefinition,
                specializationId
        );
        Map<TradeKey, TradeHistory> offerHistory = potential
                .tradePaletteFor(professionId)
                .map(palette -> palette.offerHistory())
                .orElse(Map.of());
        if (modifiers.isEmpty() && offerHistory.isEmpty()) {
            return false;
        }

        addWeightedOffers(
                villager,
                offers,
                candidates,
                requestedOfferCount,
                profession,
                villager.getVillagerData().getLevel(),
                modifiers,
                potential.careerFor(professionId)
                        .map(career -> career.learnedSkill())
                        .orElse(0.0),
                Config.specializationBiasConfig(),
                offerHistory,
                Config.seenTradeWeightMultiplier(),
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
        Objects.requireNonNull(villager, "villager");
        Objects.requireNonNull(offers, "offers");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(profession, "profession");
        Objects.requireNonNull(specialization, "specialization");
        Objects.requireNonNull(biasConfig, "biasConfig");
        Objects.requireNonNull(offerHistory, "offerHistory");
        Objects.requireNonNull(random, "random");
        if (!Double.isFinite(seenTradeWeightMultiplier)
                || seenTradeWeightMultiplier < 0.0
                || seenTradeWeightMultiplier > 1.0) {
            throw new IllegalArgumentException(
                    "seenTradeWeightMultiplier must be finite and between zero and one"
            );
        }

        if (offerHistory.isEmpty()) {
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
        int offersAdded = 0;
        while (offersAdded < requestedOfferCount && !remaining.isEmpty()) {
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
                            seenTradeWeightMultiplier
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
            double seenTradeWeightMultiplier
    ) {
        double specializationWeight = specializationWeight(
                candidate.listing(),
                profession,
                level,
                specialization,
                skill,
                biasConfig
        );
        return tradeMemoryWeight(
                specializationWeight,
                candidate.key(),
                offerHistory,
                seenTradeWeightMultiplier
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
}
