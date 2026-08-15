package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AptitudeInheritanceTest {
    private static final ProfessionId FARMER = ProfessionId.parse("minecraft:farmer");
    private static final ProfessionId LIBRARIAN = ProfessionId.parse("minecraft:librarian");
    private static final AptitudeGenerationConfig GENERATION_CONFIG = new AptitudeGenerationConfig(
            0.5,
            1.5,
            1.0,
            0.04,
            0.0
    );

    @Test
    void identicalParentsProduceTheirSharedAptitude() {
        VillagerPotentialState firstParent = state(Map.of(LIBRARIAN, 1.2));
        VillagerPotentialState secondParent = state(Map.of(LIBRARIAN, 1.2));

        VillagerPotentialState child = inherit(
                firstParent,
                secondParent,
                List.of(LIBRARIAN),
                new AptitudeInheritanceConfig(1.0, 0.0, 0.0),
                new Random(1L)
        );

        assertEquals(1.2, child.aptitudeFor(LIBRARIAN).orElseThrow());
    }

    @Test
    void veryDifferentParentsContributeEqually() {
        VillagerPotentialState child = inherit(
                state(Map.of(LIBRARIAN, 0.5)),
                state(Map.of(LIBRARIAN, 1.5)),
                List.of(LIBRARIAN),
                new AptitudeInheritanceConfig(1.0, 0.0, 0.0),
                new Random(2L)
        );

        assertEquals(1.0, child.aptitudeFor(LIBRARIAN).orElseThrow());
    }

    @Test
    void mutationCanMoveAptitudePositivelyOrNegatively() {
        AptitudeInheritanceConfig config = new AptitudeInheritanceConfig(1.0, 0.0, 0.04);
        VillagerPotentialState parent = state(Map.of(LIBRARIAN, 1.0));

        double positive = inherit(
                parent,
                parent,
                List.of(LIBRARIAN),
                config,
                gaussianRandom(0.5)
        ).aptitudeFor(LIBRARIAN).orElseThrow();
        double negative = inherit(
                parent,
                parent,
                List.of(LIBRARIAN),
                config,
                gaussianRandom(-0.5)
        ).aptitudeFor(LIBRARIAN).orElseThrow();

        assertEquals(1.1, positive, 0.000_000_1);
        assertEquals(0.9, negative, 0.000_000_1);
    }

    @Test
    void configuredInheritanceStrengthBlendsTowardNeutralMean() {
        VillagerPotentialState parent = state(Map.of(LIBRARIAN, 1.4));

        VillagerPotentialState child = inherit(
                parent,
                parent,
                List.of(LIBRARIAN),
                new AptitudeInheritanceConfig(0.25, 0.0, 0.0),
                new Random(3L)
        );

        assertEquals(1.1, child.aptitudeFor(LIBRARIAN).orElseThrow(), 0.000_000_1);
    }

    @Test
    void randomContributionUsesConfiguredAptitudeGeneration() {
        AptitudeGenerationConfig fixedGeneration = new AptitudeGenerationConfig(
                0.5,
                1.5,
                1.25,
                0.0,
                0.0
        );
        VillagerPotentialState parent = state(Map.of(LIBRARIAN, 0.5));

        VillagerPotentialState child = AptitudeInheritance.inherit(
                parent,
                parent,
                List.of(LIBRARIAN),
                fixedGeneration,
                new AptitudeInheritanceConfig(0.5, 0.5, 0.0),
                new Random(4L)
        );

        assertEquals(0.875, child.aptitudeFor(LIBRARIAN).orElseThrow());
    }

    @Test
    void missingParentProfessionFallsBackToGenerationMean() {
        VillagerPotentialState child = inherit(
                state(Map.of(LIBRARIAN, 1.4)),
                state(Map.of()),
                List.of(LIBRARIAN),
                new AptitudeInheritanceConfig(1.0, 0.0, 0.0),
                new Random(5L)
        );

        assertEquals(1.2, child.aptitudeFor(LIBRARIAN).orElseThrow(), 0.000_000_1);
    }

    @Test
    void enforcesGlobalBoundsAfterMutation() {
        VillagerPotentialState parent = state(Map.of(LIBRARIAN, 1.5, FARMER, 0.5));
        AptitudeInheritanceConfig config = new AptitudeInheritanceConfig(1.0, 0.0, 1.0);

        double upper = inherit(
                parent,
                parent,
                List.of(LIBRARIAN),
                config,
                gaussianRandom(10.0)
        ).aptitudeFor(LIBRARIAN).orElseThrow();
        double lower = inherit(
                parent,
                parent,
                List.of(FARMER),
                config,
                gaussianRandom(-10.0)
        ).aptitudeFor(FARMER).orElseThrow();

        assertEquals(GENERATION_CONFIG.maximum(), upper);
        assertEquals(GENERATION_CONFIG.minimum(), lower);
    }

    @Test
    void fixedRandomSourceIsDeterministicForUnorderedProfessions() {
        VillagerPotentialState firstParent = state(Map.of(LIBRARIAN, 0.7, FARMER, 1.3));
        VillagerPotentialState secondParent = state(Map.of(LIBRARIAN, 1.4, FARMER, 0.6));
        AptitudeInheritanceConfig config = new AptitudeInheritanceConfig(0.7, 0.2, 0.01);

        VillagerPotentialState firstChild = inherit(
                firstParent,
                secondParent,
                Set.of(LIBRARIAN, FARMER),
                config,
                new Random(867_5309L)
        );
        VillagerPotentialState secondChild = inherit(
                firstParent,
                secondParent,
                Set.of(FARMER, LIBRARIAN),
                config,
                new Random(867_5309L)
        );
        VillagerPotentialState differentChild = inherit(
                firstParent,
                secondParent,
                Set.of(LIBRARIAN, FARMER),
                config,
                new Random(867_5310L)
        );

        assertEquals(firstChild, secondChild);
        assertNotEquals(firstChild, differentChild);
        assertEquals(VillagerPotentialState.CURRENT_SCHEMA_VERSION, firstChild.schemaVersion());
        assertEquals(Set.of(LIBRARIAN, FARMER), firstChild.aptitudes().keySet());
        assertTrue(firstChild.aptitudes().values().stream().allMatch(
                aptitude -> aptitude >= GENERATION_CONFIG.minimum()
                        && aptitude <= GENERATION_CONFIG.maximum()
        ));
    }

    private static VillagerPotentialState inherit(
            VillagerPotentialState firstParent,
            VillagerPotentialState secondParent,
            Iterable<ProfessionId> professions,
            AptitudeInheritanceConfig config,
            Random random
    ) {
        return AptitudeInheritance.inherit(
                firstParent,
                secondParent,
                professions,
                GENERATION_CONFIG,
                config,
                random
        );
    }

    private static VillagerPotentialState state(Map<ProfessionId, Double> aptitudes) {
        return new VillagerPotentialState(VillagerPotentialState.CURRENT_SCHEMA_VERSION, aptitudes);
    }

    private static Random gaussianRandom(double gaussian) {
        return new Random(0L) {
            @Override
            public double nextGaussian() {
                return gaussian;
            }
        };
    }
}
