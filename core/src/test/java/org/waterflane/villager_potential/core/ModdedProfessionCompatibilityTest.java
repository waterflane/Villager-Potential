package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModdedProfessionCompatibilityTest {
    private static final ProfessionId ALCHEMIST =
            ProfessionId.parse("example_mod:alchemist");

    @Test
    void discoveredProfessionGetsLazyAptitudeCareerSkillAndGeneralSpecialization() {
        VillagerPotentialState initial = VillagerPotentialState.createDefault();
        VillagerPotentialState provisioned = AptitudeProvisioning.ensure(
                initial,
                ALCHEMIST,
                VillagerPotentialConfig.DEFAULT.aptitude(),
                new Random(42L)
        );
        VillagerPotentialState assigned = ProfessionSpecializationAssignment.enterProfession(
                provisioned,
                ALCHEMIST,
                100L,
                Optional.empty(),
                new Random(7L)
        );
        VillagerPotentialState progressed = assigned.progressActiveProfession(
                20L,
                VillagerPotentialConfig.DEFAULT.skill()
        );

        assertTrue(progressed.aptitudeFor(ALCHEMIST).isPresent());
        assertEquals(ALCHEMIST, progressed.activeProfession().orElseThrow());
        assertEquals(20L, progressed.careerFor(ALCHEMIST).orElseThrow()
                .accumulatedProfessionTime());
        assertTrue(progressed.careerFor(ALCHEMIST).orElseThrow().learnedSkill() > 0.0);
        assertEquals(SpecializationId.GENERAL,
                progressed.specializationFor(ALCHEMIST).orElseThrow());
    }

    @Test
    void existingLazyAptitudeIsStableAndNotRegenerated() {
        VillagerPotentialState first = AptitudeProvisioning.ensure(
                VillagerPotentialState.createDefault(),
                ALCHEMIST,
                VillagerPotentialConfig.DEFAULT.aptitude(),
                new Random(42L)
        );

        VillagerPotentialState repeated = AptitudeProvisioning.ensure(
                first,
                ALCHEMIST,
                VillagerPotentialConfig.DEFAULT.aptitude(),
                new Random(999L)
        );

        assertSame(first, repeated);
        assertEquals(first.aptitudeFor(ALCHEMIST).orElseThrow(),
                repeated.aptitudeFor(ALCHEMIST).orElseThrow());
    }
}
