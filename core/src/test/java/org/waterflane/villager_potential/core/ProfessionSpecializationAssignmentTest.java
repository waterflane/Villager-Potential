package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfessionSpecializationAssignmentTest {
    private static final ProfessionId LIBRARIAN = ProfessionId.parse("minecraft:librarian");
    private static final ProfessionId FARMER = ProfessionId.parse("minecraft:farmer");
    private static final SpecializationId LIBRARIAN_GENERAL =
            SpecializationId.parse("test:librarian/general");
    private static final SpecializationId ENCHANTER =
            SpecializationId.parse("test:librarian/enchanter");
    private static final SpecializationId SCRIBE =
            SpecializationId.parse("test:librarian/scribe");
    private static final SpecializationId EDITOR =
            SpecializationId.parse("test:librarian/editor");
    private static final SpecializationId HORTICULTURIST =
            SpecializationId.parse("test:farmer/horticulturist");

    @Test
    void firstAssignmentSelectsOnlyARegisteredProfessionSpecialization() {
        ProfessionSpecializationDefinition definition = definition(
                LIBRARIAN,
                LIBRARIAN_GENERAL,
                ENCHANTER,
                SCRIBE
        );

        VillagerPotentialState assigned = enter(
                VillagerPotentialState.createDefault(),
                LIBRARIAN,
                100L,
                Optional.of(definition),
                41L
        );
        VillagerPotentialState repeatedFromSameIdentity = enter(
                VillagerPotentialState.createDefault(),
                LIBRARIAN,
                100L,
                Optional.of(definition),
                41L
        );

        SpecializationId specialization = assigned.specializationFor(LIBRARIAN).orElseThrow();
        assertTrue(definition.namedSpecializations().contains(specialization));
        assertEquals(
                specialization,
                repeatedFromSameIdentity.specializationFor(LIBRARIAN).orElseThrow()
        );
    }

    @Test
    void workstationReplacementDoesNotRerollSpecialization() {
        ProfessionSpecializationDefinition initialDefinition = definition(
                LIBRARIAN,
                LIBRARIAN_GENERAL,
                ENCHANTER,
                SCRIBE
        );
        VillagerPotentialState firstCareer = enter(
                VillagerPotentialState.createDefault(),
                LIBRARIAN,
                100L,
                Optional.of(initialDefinition),
                7L
        );
        SpecializationId original = firstCareer.specializationFor(LIBRARIAN).orElseThrow();

        VillagerPotentialState unemployed = firstCareer.clearActiveProfession();
        VillagerPotentialState restored = enter(
                unemployed,
                LIBRARIAN,
                200L,
                Optional.of(definition(LIBRARIAN, LIBRARIAN_GENERAL, EDITOR)),
                99L
        );

        assertEquals(original, restored.specializationFor(LIBRARIAN).orElseThrow());
    }

    @Test
    void professionSwitchAndReturnRestoresTheCareerSpecialization() {
        VillagerPotentialState librarian = enter(
                VillagerPotentialState.createDefault(),
                LIBRARIAN,
                100L,
                Optional.of(definition(LIBRARIAN, LIBRARIAN_GENERAL, ENCHANTER)),
                1L
        );
        VillagerPotentialState farmer = enter(
                librarian,
                FARMER,
                150L,
                Optional.of(definition(
                        FARMER,
                        SpecializationId.parse("test:farmer/general"),
                        HORTICULTURIST
                )),
                2L
        );
        VillagerPotentialState returned = enter(
                farmer,
                LIBRARIAN,
                200L,
                Optional.of(definition(LIBRARIAN, LIBRARIAN_GENERAL, SCRIBE)),
                3L
        );

        assertEquals(ENCHANTER, returned.specializationFor(LIBRARIAN).orElseThrow());
        assertEquals(HORTICULTURIST, returned.specializationFor(FARMER).orElseThrow());
    }

    @Test
    void missingOrEmptyProfessionDefinitionsUseGeneral() {
        VillagerPotentialState missingDefinition = enter(
                VillagerPotentialState.createDefault(),
                LIBRARIAN,
                100L,
                Optional.empty(),
                1L
        );
        VillagerPotentialState emptyDefinition = enter(
                VillagerPotentialState.createDefault(),
                LIBRARIAN,
                100L,
                Optional.of(definition(LIBRARIAN, LIBRARIAN_GENERAL)),
                1L
        );

        assertEquals(
                SpecializationId.GENERAL,
                missingDefinition.specializationFor(LIBRARIAN).orElseThrow()
        );
        assertEquals(
                LIBRARIAN_GENERAL,
                emptyDefinition.specializationFor(LIBRARIAN).orElseThrow()
        );
    }

    @Test
    void disappearingDefinitionDoesNotReplacePersistentSpecializationIdentity() {
        VillagerPotentialState assigned = enter(
                VillagerPotentialState.createDefault(),
                LIBRARIAN,
                100L,
                Optional.of(definition(LIBRARIAN, LIBRARIAN_GENERAL, ENCHANTER)),
                1L
        );

        VillagerPotentialState afterReload = enter(
                assigned,
                LIBRARIAN,
                100L,
                Optional.empty(),
                99L
        );

        assertSame(assigned, afterReload);
        assertEquals(ENCHANTER, afterReload.specializationFor(LIBRARIAN).orElseThrow());
    }

    private static VillagerPotentialState enter(
            VillagerPotentialState state,
            ProfessionId profession,
            long assignmentTime,
            Optional<ProfessionSpecializationDefinition> definition,
            long seed
    ) {
        return ProfessionSpecializationAssignment.enterProfession(
                state,
                profession,
                assignmentTime,
                definition,
                new Random(seed)
        );
    }

    private static ProfessionSpecializationDefinition definition(
            ProfessionId profession,
            SpecializationId general,
            SpecializationId... specializations
    ) {
        return new ProfessionSpecializationDefinition(
                profession,
                general,
                List.of(specializations)
        );
    }
}
