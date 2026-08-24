package org.waterflane.villager_potential;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class VillagerPotentialDiagnosticsTest {
    private static final UUID VILLAGER =
            UUID.fromString("00000000-0000-0000-0000-000000000123");

    @Test
    void disabledDiagnosticsEmitNoLogMessage() {
        Logger logger = mock(Logger.class);

        VillagerPotentialDiagnostics.writeInitialization(logger, false, VILLAGER, 10, 13);

        verify(logger, never()).info(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void enabledDiagnosticsEmitRepresentativeConciseEvent() {
        Logger logger = mock(Logger.class);

        VillagerPotentialDiagnostics.writeInitialization(logger, true, VILLAGER, 10, 13);

        verify(logger).info("[Villager Potential/debug] initialized villager=" + VILLAGER
                + " schema=10 aptitudes=13");
    }
}
