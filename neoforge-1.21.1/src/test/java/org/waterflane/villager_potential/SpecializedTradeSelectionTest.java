package org.waterflane.villager_potential;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.ProfessionSpecializationDefinition;
import org.waterflane.villager_potential.core.SpecializationBiasConfig;
import org.waterflane.villager_potential.core.SpecializationDefinition;
import org.waterflane.villager_potential.core.SpecializationId;
import org.waterflane.villager_potential.core.TradeCategoryId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SpecializedTradeSelectionTest {
    private static final ProfessionId LIBRARIAN = ProfessionId.parse("minecraft:librarian");
    private static final SpecializationId GENERAL = SpecializationId.parse("test:librarian/general");
    private static final SpecializationId ENCHANTER = SpecializationId.parse("test:librarian/enchanter");
    private static final TradeCategoryId MATCHING = TradeCategoryId.parse("test:matching");
    private static final TradeCategoryId OFF_CATEGORY = TradeCategoryId.parse("test:off_category");
    private static final SpecializationBiasConfig BIAS_CONFIG = new SpecializationBiasConfig(
            0.0,
            1.0,
            0.1,
            1.0,
            2.0
    );

    @Test
    void allNeutralModifiersMatchVanillaSelection() {
        SpecializationDefinition general = new SpecializationDefinition(GENERAL, Map.of());
        ProfessionSpecializationDefinition profession = new ProfessionSpecializationDefinition(
                LIBRARIAN,
                general,
                List.of(new SpecializationDefinition(ENCHANTER, Map.of(MATCHING, 4.0)))
        );
        Optional<SpecializationDefinition> modifiers = SpecializedTradeSelection.selectionModifiers(
                Optional.of(profession),
                Optional.of(GENERAL)
        );
        assertTrue(modifiers.isEmpty());

        MerchantOffer first = mock(MerchantOffer.class);
        MerchantOffer second = mock(MerchantOffer.class);
        MerchantOffer third = mock(MerchantOffer.class);
        VillagerTrades.ItemListing[] candidates = {
                listing(first, MATCHING),
                listing(second, OFF_CATEGORY),
                listing(third, OFF_CATEGORY)
        };
        Villager villager = mock(Villager.class);
        MerchantOffers expected = new MerchantOffers();
        MerchantOffers actual = new MerchantOffers();

        vanillaAddOffers(villager, expected, candidates, 2, RandomSource.create(90125L));
        SpecializedTradeSelection.addWeightedOffers(
                villager,
                actual,
                candidates,
                2,
                VillagerProfession.LIBRARIAN,
                1,
                modifiers,
                1.0,
                BIAS_CONFIG,
                Map.of(),
                1.0,
                org.waterflane.villager_potential.core.TradePaletteRerollStrategy.VANILLA,
                RandomSource.create(90125L)
        );

        assertEquals(expected, actual);
    }

    @Test
    void matchingCategoryGainsRelativeWeight() {
        SelectionCounts counts = sampleSelections(20_000, 4.0, 1.0, BIAS_CONFIG);

        assertTrue(counts.matching() > 15_000, counts.toString());
        assertTrue(counts.matching() > counts.offCategory(), counts.toString());
    }

    @Test
    void offCategoryRemainsPossible() {
        SelectionCounts counts = sampleSelections(20_000, 4.0, 1.0, BIAS_CONFIG);

        assertTrue(counts.offCategory() > 0, counts.toString());
    }

    @Test
    void candidatePoolIsUnchanged() {
        MerchantOffer matchingOffer = mock(MerchantOffer.class);
        MerchantOffer offCategoryOffer = mock(MerchantOffer.class);
        VillagerTrades.ItemListing matching = listing(matchingOffer, MATCHING);
        VillagerTrades.ItemListing offCategory = listing(offCategoryOffer, OFF_CATEGORY);
        VillagerTrades.ItemListing[] candidates = {matching, offCategory};
        VillagerTrades.ItemListing[] original = candidates.clone();

        SpecializedTradeSelection.addWeightedOffers(
                mock(Villager.class),
                new MerchantOffers(),
                candidates,
                1,
                VillagerProfession.LIBRARIAN,
                1,
                specialization(4.0),
                1.0,
                BIAS_CONFIG,
                RandomSource.create(17L)
        );

        assertArrayEquals(original, candidates);
        assertSame(matching, candidates[0]);
        assertSame(offCategory, candidates[1]);
    }

    @Test
    void nullFactoryDoesNotConsumeAnOfferSlot() {
        MerchantOffer successfulOffer = mock(MerchantOffer.class);
        VillagerTrades.ItemListing nullFactory = listing(null, MATCHING);
        VillagerTrades.ItemListing successfulFactory = listing(successfulOffer, OFF_CATEGORY);
        MerchantOffers offers = new MerchantOffers();

        SpecializedTradeSelection.addWeightedOffers(
                mock(Villager.class),
                offers,
                new VillagerTrades.ItemListing[]{nullFactory, successfulFactory},
                1,
                VillagerProfession.LIBRARIAN,
                1,
                specialization(100.0),
                1.0,
                BIAS_CONFIG,
                RandomSource.create(0L)
        );

        assertEquals(1, offers.size());
        assertSame(successfulOffer, offers.getFirst());
    }

    @Test
    void zeroWeightExplicitlyDisablesCategory() {
        MerchantOffer matchingOffer = mock(MerchantOffer.class);
        MerchantOffer offCategoryOffer = mock(MerchantOffer.class);
        MerchantOffers offers = new MerchantOffers();

        SpecializedTradeSelection.addWeightedOffers(
                mock(Villager.class),
                offers,
                new VillagerTrades.ItemListing[]{
                        listing(matchingOffer, MATCHING),
                        listing(offCategoryOffer, OFF_CATEGORY)
                },
                2,
                VillagerProfession.LIBRARIAN,
                1,
                specialization(0.0),
                1.0,
                BIAS_CONFIG,
                RandomSource.create(0L)
        );

        assertEquals(1, offers.size());
        assertSame(offCategoryOffer, offers.getFirst());
        assertFalse(offers.contains(matchingOffer));
    }

    @Test
    void experiencedVillagersHaveStrongerSelectionBiasThanLowSkillVillagers() {
        SelectionCounts lowSkill = sampleSelections(40_000, 4.0, 0.0, BIAS_CONFIG);
        SelectionCounts highSkill = sampleSelections(40_000, 4.0, 1.0, BIAS_CONFIG);

        assertTrue(lowSkill.matching() > lowSkill.offCategory(), lowSkill.toString());
        assertTrue(highSkill.matching() > lowSkill.matching() + 8_000,
                "low=" + lowSkill + ", high=" + highSkill);
    }

    @Test
    void biasCannotSelectATradeOutsideTheVanillaCandidatePool() {
        MerchantOffer vanillaOffer = mock(MerchantOffer.class);
        MerchantOffer unavailableOffer = mock(MerchantOffer.class);
        VillagerTrades.ItemListing[] vanillaCandidates = {listing(vanillaOffer, OFF_CATEGORY)};

        for (int attempt = 0; attempt < 100; attempt++) {
            MerchantOffers offers = new MerchantOffers();
            SpecializedTradeSelection.addWeightedOffers(
                    mock(Villager.class),
                    offers,
                    vanillaCandidates,
                    1,
                    VillagerProfession.LIBRARIAN,
                    1,
                    specialization(1_000_000.0),
                    1.0,
                    BIAS_CONFIG,
                    RandomSource.create(attempt)
            );
            assertEquals(List.of(vanillaOffer), offers);
            assertFalse(offers.contains(unavailableOffer));
        }
    }

    private static SelectionCounts sampleSelections(
            int attempts,
            double matchingWeight,
            double skill,
            SpecializationBiasConfig biasConfig
    ) {
        MerchantOffer matchingOffer = mock(MerchantOffer.class);
        MerchantOffer offCategoryOffer = mock(MerchantOffer.class);
        VillagerTrades.ItemListing[] candidates = {
                listing(matchingOffer, MATCHING),
                listing(offCategoryOffer, OFF_CATEGORY)
        };
        Villager villager = mock(Villager.class);
        RandomSource random = RandomSource.create(4589123L);
        int matchingSelections = 0;
        int offCategorySelections = 0;

        for (int attempt = 0; attempt < attempts; attempt++) {
            MerchantOffers offers = new MerchantOffers();
            SpecializedTradeSelection.addWeightedOffers(
                    villager,
                    offers,
                    candidates,
                    1,
                    VillagerProfession.LIBRARIAN,
                    1,
                    specialization(matchingWeight),
                    skill,
                    biasConfig,
                    random
            );
            if (offers.getFirst() == matchingOffer) {
                matchingSelections++;
            } else if (offers.getFirst() == offCategoryOffer) {
                offCategorySelections++;
            }
        }
        return new SelectionCounts(matchingSelections, offCategorySelections);
    }

    private static SpecializationDefinition specialization(double matchingWeight) {
        return new SpecializationDefinition(ENCHANTER, Map.of(MATCHING, matchingWeight));
    }

    private static VillagerTrades.ItemListing listing(
            MerchantOffer offer,
            TradeCategoryId category
    ) {
        return new ClassifiedItemListing((entity, random) -> offer, category);
    }

    private static void vanillaAddOffers(
            Villager villager,
            MerchantOffers offers,
            VillagerTrades.ItemListing[] candidates,
            int requestedOfferCount,
            RandomSource random
    ) {
        List<VillagerTrades.ItemListing> remaining = new ArrayList<>(Arrays.asList(candidates));
        int offersAdded = 0;
        while (offersAdded < requestedOfferCount && !remaining.isEmpty()) {
            MerchantOffer offer = remaining.remove(random.nextInt(remaining.size()))
                    .getOffer(villager, random);
            if (offer != null) {
                offers.add(offer);
                offersAdded++;
            }
        }
    }

    private record SelectionCounts(int matching, int offCategory) {
    }
}
