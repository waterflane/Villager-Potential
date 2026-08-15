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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Applies specialization weights to the candidate array selected by vanilla.
 *
 * <p>The input array is never mutated. Selection remains sampling without
 * replacement, and a listing that returns {@code null} is discarded without
 * consuming one of the requested offer slots, matching vanilla semantics.</p>
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
        if (professionDefinition.isEmpty()) {
            return false;
        }

        var potential = VillagerPotentialAttachments.get(villager);
        Optional<SpecializationId> specializationId = potential.specializationFor(professionId);
        Optional<SpecializationDefinition> modifiers = selectionModifiers(
                professionDefinition,
                specializationId
        );
        if (modifiers.isEmpty()) {
            return false;
        }

        addWeightedOffers(
                villager,
                offers,
                candidates,
                requestedOfferCount,
                profession,
                villager.getVillagerData().getLevel(),
                modifiers.orElseThrow(),
                potential.careerFor(professionId).orElseThrow().learnedSkill(),
                Config.specializationBiasConfig(),
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
        Objects.requireNonNull(villager, "villager");
        Objects.requireNonNull(offers, "offers");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(profession, "profession");
        Objects.requireNonNull(specialization, "specialization");
        Objects.requireNonNull(biasConfig, "biasConfig");
        Objects.requireNonNull(random, "random");

        List<VillagerTrades.ItemListing> remaining = new ArrayList<>(Arrays.asList(candidates));
        int offersAdded = 0;
        while (offersAdded < requestedOfferCount && !remaining.isEmpty()) {
            int selectedIndex = weightedIndex(
                    remaining,
                    profession,
                    level,
                    specialization,
                    skill,
                    biasConfig,
                    random
            );
            if (selectedIndex < 0) {
                break;
            }

            VillagerTrades.ItemListing selected = remaining.remove(selectedIndex);
            MerchantOffer offer = selected.getOffer(villager, random);
            if (offer != null) {
                offers.add(offer);
                offersAdded++;
            }
        }
    }

    private static int weightedIndex(
            List<VillagerTrades.ItemListing> candidates,
            VillagerProfession profession,
            int level,
            SpecializationDefinition specialization,
            double skill,
            SpecializationBiasConfig biasConfig,
            RandomSource random
    ) {
        double maximumWeight = 0.0;
        for (VillagerTrades.ItemListing candidate : candidates) {
            maximumWeight = Math.max(
                    maximumWeight,
                    weight(candidate, profession, level, specialization, skill, biasConfig)
            );
        }
        if (maximumWeight == 0.0) {
            return -1;
        }

        double normalizedTotal = 0.0;
        for (VillagerTrades.ItemListing candidate : candidates) {
            normalizedTotal += weight(
                    candidate,
                    profession,
                    level,
                    specialization,
                    skill,
                    biasConfig
            ) / maximumWeight;
        }

        double target = random.nextDouble() * normalizedTotal;
        int lastPositiveIndex = -1;
        double cumulativeWeight = 0.0;
        for (int index = 0; index < candidates.size(); index++) {
            double normalizedWeight = weight(
                    candidates.get(index),
                    profession,
                    level,
                    specialization,
                    skill,
                    biasConfig
            ) / maximumWeight;
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
            VillagerTrades.ItemListing candidate,
            VillagerProfession profession,
            int level,
            SpecializationDefinition specialization,
            double skill,
            SpecializationBiasConfig biasConfig
    ) {
        return biasConfig.weightModifier(
                specialization.weightModifierFor(
                        VanillaTradeClassifications.classify(profession, level, candidate)
                ),
                skill
        );
    }
}
