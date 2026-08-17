package org.waterflane.villager_potential.core;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.stream.StreamSupport;

/**
 * Produces child aptitude state without depending on a game platform.
 *
 * <p>This operation intentionally accepts and returns only aptitude state. Skill,
 * career history, specialization, trade memory, demand, and other runtime state
 * are outside the inheritance boundary.</p>
 */
public final class AptitudeInheritance {
    private static final Comparator<ProfessionId> PROFESSION_ORDER = Comparator
            .comparing(ProfessionId::namespace)
            .thenComparing(ProfessionId::path);

    private AptitudeInheritance() {
    }

    /**
     * Inherits aptitude for each requested profession.
     *
     * <p>A missing aptitude on either parent falls back to the generation mean.
     * Professions are processed in identifier order, so an unordered input
     * collection still produces deterministic results with a fixed random source.</p>
     */
    public static VillagerPotentialState inherit(
            VillagerPotentialState firstParent,
            VillagerPotentialState secondParent,
            Iterable<ProfessionId> professions,
            AptitudeGenerationConfig generationConfig,
            AptitudeInheritanceConfig inheritanceConfig,
            RandomGenerator random
    ) {
        Objects.requireNonNull(firstParent, "firstParent");
        Objects.requireNonNull(secondParent, "secondParent");
        Objects.requireNonNull(professions, "professions");
        Objects.requireNonNull(generationConfig, "generationConfig");
        Objects.requireNonNull(inheritanceConfig, "inheritanceConfig");
        Objects.requireNonNull(random, "random");

        Map<ProfessionId, Double> childAptitudes = new LinkedHashMap<>();
        StreamSupport.stream(professions.spliterator(), false)
                .peek(profession -> Objects.requireNonNull(profession, "profession"))
                .distinct()
                .sorted(PROFESSION_ORDER)
                .forEach(profession -> childAptitudes.put(
                        profession,
                        inheritAptitude(
                                firstParent,
                                secondParent,
                                profession,
                                generationConfig,
                                inheritanceConfig,
                                random
                        )
                ));

        return new VillagerPotentialState(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                childAptitudes
        );
    }

    private static double inheritAptitude(
            VillagerPotentialState firstParent,
            VillagerPotentialState secondParent,
            ProfessionId profession,
            AptitudeGenerationConfig generationConfig,
            AptitudeInheritanceConfig inheritanceConfig,
            RandomGenerator random
    ) {
        if (!inheritanceConfig.enabled()) {
            return AptitudeGenerator.generate(generationConfig, random);
        }
        double firstAptitude = boundedParentAptitude(firstParent, profession, generationConfig);
        double secondAptitude = boundedParentAptitude(secondParent, profession, generationConfig);
        double parentAverage = (firstAptitude + secondAptitude) / 2.0;
        double neutralContribution = 1.0
                - inheritanceConfig.inheritanceStrength()
                - inheritanceConfig.randomContribution();

        double inherited = parentAverage * inheritanceConfig.inheritanceStrength()
                + generationConfig.mean() * neutralContribution;
        if (inheritanceConfig.randomContribution() > 0.0) {
            inherited += AptitudeGenerator.generate(generationConfig, random)
                    * inheritanceConfig.randomContribution();
        }
        if (inheritanceConfig.mutationVariance() > 0.0
                && (inheritanceConfig.mutationChance() >= 1.0
                || random.nextDouble() < inheritanceConfig.mutationChance())) {
            inherited += random.nextGaussian() * Math.sqrt(inheritanceConfig.mutationVariance());
        }

        return clamp(inherited, generationConfig.minimum(), generationConfig.maximum());
    }

    private static double boundedParentAptitude(
            VillagerPotentialState parent,
            ProfessionId profession,
            AptitudeGenerationConfig generationConfig
    ) {
        double aptitude = parent.aptitudeFor(profession).orElse(generationConfig.mean());
        return clamp(aptitude, generationConfig.minimum(), generationConfig.maximum());
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
