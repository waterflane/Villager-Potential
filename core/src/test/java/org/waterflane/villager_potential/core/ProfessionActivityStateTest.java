package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfessionActivityStateTest {
    private static final ProfessionId LIBRARIAN = ProfessionId.parse("minecraft:librarian");
    private static final ProfessionId FARMER = ProfessionId.parse("minecraft:farmer");
    private static final ProfessionActivityConfig CONFIG = new ProfessionActivityConfig(
            0.2,
            0.8,
            0.15,
            0.01
    );

    @Test
    void tradeIncreasesProfessionActivityWithoutAddingSkill() {
        ProfessionCareerState career = ProfessionCareerState.firstAssignedAt(10L)
                .withLearnedSkill(0.45);
        VillagerPotentialState state = new VillagerPotentialState(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                Map.of(LIBRARIAN, 1.25)
        ).assignProfession(LIBRARIAN, 10L).withCareer(LIBRARIAN, career);

        VillagerPotentialState traded = state.recordProfessionTrade(LIBRARIAN, 100L, CONFIG);

        assertEquals(0.35, traded.professionActivityFor(LIBRARIAN, 100L, CONFIG));
        assertEquals(career, traded.careerFor(LIBRARIAN).orElseThrow());
    }

    @Test
    void repeatedTradesRespectTheConfiguredCap() {
        VillagerPotentialState state = VillagerPotentialState.createDefault();

        for (int trade = 0; trade < 20; trade++) {
            state = state.recordProfessionTrade(LIBRARIAN, 100L, CONFIG);
        }

        assertEquals(CONFIG.maximum(), state.professionActivityFor(LIBRARIAN, 100L, CONFIG));
    }

    @Test
    void activityDecaysTowardBaseline() {
        VillagerPotentialState traded = VillagerPotentialState.createDefault()
                .recordProfessionTrade(LIBRARIAN, 100L, CONFIG);

        assertEquals(0.30, traded.professionActivityFor(LIBRARIAN, 105L, CONFIG), 0.000_000_1);
        assertEquals(CONFIG.baseline(), traded.professionActivityFor(LIBRARIAN, 1_000L, CONFIG));
    }

    @Test
    void tradeLeavesOtherProfessionsUnaffected() {
        VillagerPotentialState state = VillagerPotentialState.createDefault()
                .recordProfessionTrade(FARMER, 100L, CONFIG);
        ProfessionActivityState farmerActivity = state.professionActivities().get(FARMER);

        VillagerPotentialState traded = state.recordProfessionTrade(LIBRARIAN, 100L, CONFIG);

        assertEquals(farmerActivity, traded.professionActivities().get(FARMER));
        assertEquals(0.35, traded.professionActivityFor(FARMER, 100L, CONFIG));
        assertEquals(0.35, traded.professionActivityFor(LIBRARIAN, 100L, CONFIG));
    }
}
