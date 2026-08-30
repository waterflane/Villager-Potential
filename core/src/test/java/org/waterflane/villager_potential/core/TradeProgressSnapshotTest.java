package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeProgressSnapshotTest {
    private static final ProfessionId FARMER = new ProfessionId("minecraft", "farmer");

    @Test
    void createsTheSharedOverlaySnapshot() {
        ProfessionCareerState career = new ProfessionCareerState(
                20L, 0.75, 0L, 0L, Optional.empty()
        );
        VillagerPotentialState state = new VillagerPotentialState(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                Map.of(FARMER, 1.37),
                Map.of(FARMER, career),
                Optional.of(FARMER)
        );

        TradeProgressSnapshot snapshot = TradeProgressSnapshot.create(
                state, FARMER, 1, 20L, true, VillagerPotentialConfig.DEFAULT
        ).orElseThrow();

        assertEquals(1.37, snapshot.aptitudeMultiplier(), 0.000_000_1);
        assertEquals(0.5, snapshot.skillFraction(), 0.000_000_1);
        assertTrue(snapshot.skillPerMinute() > 0.0);
    }

    @Test
    void reportsCompletedAndClampedProgress() {
        TradeProgressSnapshot snapshot = snapshot(1.5, 0.5, 1.0, 2.5, 0.0);

        assertEquals(1.0, snapshot.skillFraction());
        assertEquals(1.0, snapshot.activityFraction());
        assertEquals(0.0, snapshot.minutesRemaining());
    }

    @Test
    void reportsPausedProgress() {
        TradeProgressSnapshot snapshot = snapshot(0.75, 0.5, 1.0, 1.0, 0.0);

        assertEquals(Double.POSITIVE_INFINITY, snapshot.minutesRemaining());
    }

    @Test
    void masterSkillBarIsEmpty() {
        TradeProgressSnapshot snapshot = new TradeProgressSnapshot(
                5, 10.5, 10.5, 10.5, 0.0, 0.0,
                1.4, 1.0, 1.0, 2.0, 0.1
        );

        assertEquals(0.0, snapshot.skillFraction());
        assertEquals(0.0, snapshot.minutesRemaining());
    }

    private static TradeProgressSnapshot snapshot(
            double skill,
            double levelStart,
            double nextLevel,
            double activity,
            double rate
    ) {
        return new TradeProgressSnapshot(
                3, skill, levelStart, nextLevel, 0.05, rate,
                1.37, activity, 1.0, 2.0, 0.1
        );
    }
}
