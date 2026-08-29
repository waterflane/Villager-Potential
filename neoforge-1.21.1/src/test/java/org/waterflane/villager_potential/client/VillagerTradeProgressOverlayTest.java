package org.waterflane.villager_potential.client;

import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.VillagerTradeProgressPayload;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VillagerTradeProgressOverlayTest {
    @Test
    void skillBarTracksOnlyTheCurrentLevelInterval() {
        VillagerTradeProgressPayload payload = payload(0.75, 0.5, 1.0, 1.0);

        assertEquals(0.5, VillagerTradeProgressOverlay.skillFraction(payload));
    }

    @Test
    void activityBarTracksTheConfiguredBaselineToMaximumRange() {
        VillagerTradeProgressPayload payload = payload(0.75, 0.5, 1.0, 1.4);

        assertEquals(0.4, VillagerTradeProgressOverlay.activityFraction(payload), 0.000_000_1);
    }

    @Test
    void bothBarsClampOvershoot() {
        VillagerTradeProgressPayload payload = payload(1.5, 0.5, 1.0, 2.5);

        assertEquals(1.0, VillagerTradeProgressOverlay.skillFraction(payload));
        assertEquals(1.0, VillagerTradeProgressOverlay.activityFraction(payload));
    }

    @Test
    void skillBarIsEmptyAfterReachingMaster() {
        VillagerTradeProgressPayload payload = new VillagerTradeProgressPayload(
                7,
                5,
                10.5,
                10.5,
                10.5,
                0.0,
                1.0,
                1.0,
                2.0,
                0.1
        );

        assertEquals(0.0, VillagerTradeProgressOverlay.skillFraction(payload));
    }

    @Test
    void remainingSkillIsConvertedToMinutesAtTheCurrentRate() {
        VillagerTradeProgressPayload payload = payload(0.75, 0.5, 1.0, 1.0);

        assertEquals(
                (1.0 - 0.75) / 0.075,
                VillagerTradeProgressOverlay.minutesRemaining(payload),
                0.000_000_1
        );
    }

    @Test
    void stoppedProgressHasNoFiniteCompletionEstimate() {
        VillagerTradeProgressPayload payload = new VillagerTradeProgressPayload(
                7, 3, 0.75, 0.5, 1.0, 0.0, 1.0, 1.0, 2.0, 0.1
        );

        assertEquals(
                Double.POSITIVE_INFINITY,
                VillagerTradeProgressOverlay.minutesRemaining(payload)
        );
    }

    private static VillagerTradeProgressPayload payload(
            double skill,
            double levelStart,
            double nextLevel,
            double activity
    ) {
        return new VillagerTradeProgressPayload(
                7,
                3,
                skill,
                levelStart,
                nextLevel,
                0.075,
                activity,
                1.0,
                2.0,
                0.1
        );
    }
}
