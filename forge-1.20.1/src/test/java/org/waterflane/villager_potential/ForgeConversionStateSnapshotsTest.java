package org.waterflane.villager_potential;

import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeConversionStateSnapshotsTest {
    private static final ProfessionId FARMER = ProfessionId.parse("minecraft:farmer");

    @Test
    void postConversionTakesStateCapturedBeforeSourceInvalidation() {
        ForgeConversionStateSnapshots<Object> snapshots =
                new ForgeConversionStateSnapshots<>();
        Object sourceEntity = new Object();
        VillagerPotentialState state = new VillagerPotentialState(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                Map.of(FARMER, 1.25)
        );

        snapshots.remember(sourceEntity, state);

        assertSame(state, snapshots.take(sourceEntity).orElseThrow());
        assertTrue(snapshots.take(sourceEntity).isEmpty());
    }

    @Test
    void unrelatedConversionsRemainIndependent() {
        ForgeConversionStateSnapshots<Object> snapshots =
                new ForgeConversionStateSnapshots<>();
        Object firstSource = new Object();
        Object secondSource = new Object();
        VillagerPotentialState firstState = state(0.75);
        VillagerPotentialState secondState = state(1.5);

        snapshots.remember(firstSource, firstState);
        snapshots.remember(secondSource, secondState);

        assertSame(secondState, snapshots.take(secondSource).orElseThrow());
        assertSame(firstState, snapshots.take(firstSource).orElseThrow());
    }

    private static VillagerPotentialState state(double aptitude) {
        return new VillagerPotentialState(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                Map.of(FARMER, aptitude)
        );
    }
}
