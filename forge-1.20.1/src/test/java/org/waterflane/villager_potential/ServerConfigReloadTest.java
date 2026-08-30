package org.waterflane.villager_potential;

import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.MarketDemandState;
import org.waterflane.villager_potential.core.ProfessionCareerState;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.SpecializationConfig;
import org.waterflane.villager_potential.core.SpecializationId;
import org.waterflane.villager_potential.core.TradeHistory;
import org.waterflane.villager_potential.core.TradeKey;
import org.waterflane.villager_potential.core.TradePaletteState;
import org.waterflane.villager_potential.core.VillagerPotentialConfig;
import org.waterflane.villager_potential.core.VillagerPotentialConfiguration;
import org.waterflane.villager_potential.core.VillagerPotentialState;
import org.waterflane.villager_potential.core.VillagerTradeConfig;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerConfigReloadTest {
    private static final ProfessionId LIBRARIAN =
            ProfessionId.parse("minecraft:librarian");
    private static final SpecializationId ENCHANTER =
            SpecializationId.parse("test:librarian/enchanter");

    @Test
    void successfulReloadPublishesValidatedSettingsProspectivelyWithoutTouchingState() {
        VillagerPotentialConfiguration previous = ServerConfig.activeConfiguration();
        VillagerPotentialConfiguration candidate = candidateConfiguration();
        VillagerPotentialState state = persistentState();
        VillagerPotentialState before = state;
        TradePaletteState paletteBefore = state.tradePaletteFor(LIBRARIAN).orElseThrow();
        AtomicBoolean resourcesReloaded = new AtomicBoolean();

        try {
            ServerConfig.reload(candidate, () -> {
                resourcesReloaded.set(true);
                return CompletableFuture.completedFuture(null);
            }).join();

            assertTrue(resourcesReloaded.get());
            assertSame(candidate, ServerConfig.activeConfiguration());
            assertSame(candidate.gameplay(), ServerConfig.gameplayConfig());
            assertSame(candidate.trades(), ServerConfig.tradeConfig());
            assertEquals(before, state);
            assertEquals(1.35, state.aptitudeFor(LIBRARIAN).orElseThrow());
            assertEquals(8_000L, state.careerFor(LIBRARIAN).orElseThrow()
                    .accumulatedProfessionTime());
            assertEquals(0.65, state.careerFor(LIBRARIAN).orElseThrow().learnedSkill());
            assertEquals(ENCHANTER, state.specializationFor(LIBRARIAN).orElseThrow());
            assertSame(paletteBefore, state.tradePaletteFor(LIBRARIAN).orElseThrow());
            assertEquals(
                    before.marketDemand(),
                    state.marketDemand()
            );
        } finally {
            ServerConfig.activate(previous);
        }
    }

    @Test
    void failedDataReloadKeepsTheLastValidatedRuntimeConfiguration() {
        VillagerPotentialConfiguration previous = ServerConfig.activeConfiguration();

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> ServerConfig.reload(
                        candidateConfiguration(),
                        () -> CompletableFuture.failedFuture(
                                new JsonParseException("malformed specialization test:broken")
                        )
                ).join()
        );

        assertTrue(exception.getCause().getMessage().contains("malformed specialization"));
        assertSame(previous, ServerConfig.activeConfiguration());
    }

    private static VillagerPotentialConfiguration candidateConfiguration() {
        VillagerTradeConfig defaults = VillagerTradeConfig.DEFAULT;
        return new VillagerPotentialConfiguration(
                VillagerPotentialConfig.DEFAULT,
                new VillagerTradeConfig(
                        new SpecializationConfig(
                                true, 0.55, 0.2, 0.9, 1.5,
                                Map.of(LIBRARIAN, 0.75)
                        ),
                        defaults.palette(),
                        defaults.economy()
                )
        );
    }

    private static VillagerPotentialState persistentState() {
        TradeKey trade = new TradeKey.Fallback("librarian-book-offer");
        TradePaletteState palette = new TradePaletteState(
                List.of(trade),
                Map.of(trade, TradeHistory.seenAt(100L).recordUsed(120L))
        );
        ProfessionCareerState career = new ProfessionCareerState(
                8_000L,
                0.65,
                20L,
                60L,
                Optional.of(ENCHANTER)
        );
        return new VillagerPotentialState(
                VillagerPotentialState.CURRENT_SCHEMA_VERSION,
                Map.of(LIBRARIAN, 1.35),
                Map.of(LIBRARIAN, career),
                Optional.of(LIBRARIAN),
                Map.of(),
                Map.of(LIBRARIAN, palette),
                Map.of(LIBRARIAN, Map.of(
                        trade,
                        new MarketDemandState(4.5, 3L, 120L)
                ))
        );
    }
}
