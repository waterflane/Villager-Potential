package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfessionProgressBatchTest {
    private static final ProfessionId LIBRARIAN = ProfessionId.parse("minecraft:librarian");

    @Test
    void accumulatesEligibleTicksUntilTheFlushInterval() {
        ProfessionProgressBatch batch =
                new ProfessionProgressBatch(LIBRARIAN, 0L, 100L, 1);

        for (int tick = 0; tick < ProfessionProgressBatch.FLUSH_INTERVAL_TICKS - 1; tick++) {
            batch.addElapsedTick();
            assertFalse(batch.reachedFlushInterval(), "flushed before interval");
        }
        batch.addElapsedTick();
        assertTrue(batch.reachedFlushInterval());

        batch.clearElapsedTime();
        assertEquals(0L, batch.elapsedProfessionTime());
        assertFalse(batch.reachedFlushInterval());
    }

    @Test
    void gameTimeObservationIsMonotonicAndVanillaLevelIsRetained() {
        ProfessionProgressBatch batch =
                new ProfessionProgressBatch(LIBRARIAN, 5L, 200L, 2);

        batch.observeGameTime(150L);
        assertEquals(200L, batch.lastObservedGameTime());
        batch.observeGameTime(260L);
        assertEquals(260L, batch.lastObservedGameTime());

        batch.observeVanillaLevel(3);
        assertEquals(3, batch.vanillaLevel());
        assertEquals(LIBRARIAN, batch.profession());
        assertEquals(5L, batch.elapsedProfessionTime());
    }

    @Test
    void toleratesUnemployedBatchesAndArbitraryElapsedValues() {
        // Unemployed villagers keep a batch so elapsed time and game-time
        // observation survive until they take up a profession.
        ProfessionProgressBatch unemployed =
                new ProfessionProgressBatch(null, 0L, 10L, 1);
        unemployed.addElapsedTick();
        assertEquals(1L, unemployed.elapsedProfessionTime());
        assertTrue(unemployed.lastObservedGameTime() == 10L);
        assertFalse(unemployed.reachedFlushInterval());
    }
}
