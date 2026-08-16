package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TradeSelectionResolverTest {
    private static final SpecializationBiasConfig FULL_STRENGTH =
            new SpecializationBiasConfig(0.0, 1.0, 1.0, 1.0, 1.0);
    private static final TradeMemoryRecoveryConfig MEMORY =
            new TradeMemoryRecoveryConfig(100L, 0.0, 100L, 100L, 0L);

    @Test
    void neutralModifiersUseVanillaUniformSelection() {
        TrackingRandom random = new TrackingRandom(2, 0.0);

        int selected = TradeSelectionResolver.selectIndex(
                List.of(candidate(1.0, 1.0, null), candidate(1.0, 1.0, null),
                        candidate(1.0, 1.0, null)),
                rules(FULL_STRENGTH, 1.0, TradePaletteRerollStrategy.VANILLA),
                random
        );

        assertEquals(2, selected);
        assertEquals(1, random.nextIntCalls);
        assertEquals(0, random.nextDoubleCalls);
    }

    @Test
    void specializationModifierIsApplied() {
        assertEquals(4.0, TradeSelectionResolver.resolvedWeight(
                candidate(4.0, 1.0, null),
                rules(FULL_STRENGTH, 1.0, TradePaletteRerollStrategy.VANILLA)
        ));
    }

    @Test
    void skillControlsSpecializationStrength() {
        SpecializationBiasConfig skillStrength =
                new SpecializationBiasConfig(0.0, 1.0, 0.0, 1.0, 1.0);

        assertEquals(1.0, TradeSelectionResolver.resolvedWeight(
                candidate(4.0, 1.0, null),
                rules(skillStrength, 0.0, TradePaletteRerollStrategy.VANILLA)
        ));
        assertEquals(4.0, TradeSelectionResolver.resolvedWeight(
                candidate(4.0, 1.0, null),
                rules(skillStrength, 1.0, TradePaletteRerollStrategy.VANILLA)
        ));
    }

    @Test
    void weightedMemoryComposesAfterSpecializationAndBeforeOverride() {
        assertEquals(2.0, TradeSelectionResolver.resolvedWeight(
                candidate(4.0, 2.0, TradeHistory.seenAt(0L)),
                rules(FULL_STRENGTH, 1.0, TradePaletteRerollStrategy.WEIGHTED_MEMORY)
        ));
    }

    @Test
    void invalidAndNegativeWeightsAreSafelyUnavailable() {
        List<TradeSelectionResolver.Candidate> candidates = List.of(
                candidate(Double.NaN, 1.0, null),
                candidate(-1.0, 1.0, null),
                candidate(1.0, 1.0, null)
        );

        assertEquals(2, TradeSelectionResolver.selectIndex(
                candidates,
                rules(FULL_STRENGTH, 1.0, TradePaletteRerollStrategy.VANILLA),
                new TrackingRandom(0, 0.0)
        ));
    }

    private static TradeSelectionResolver.Candidate candidate(
            double specialization,
            double override,
            TradeHistory history
    ) {
        return new TradeSelectionResolver.Candidate(
                1.0,
                specialization,
                override,
                history,
                false
        );
    }

    private static TradeSelectionResolver.Rules rules(
            SpecializationBiasConfig bias,
            double skill,
            TradePaletteRerollStrategy strategy
    ) {
        return new TradeSelectionResolver.Rules(
                skill,
                bias,
                strategy,
                0L,
                0.25,
                MEMORY,
                0L,
                false
        );
    }

    private static final class TrackingRandom implements TradeSelectionResolver.SelectionRandom {
        private final int nextInt;
        private final double nextDouble;
        private int nextIntCalls;
        private int nextDoubleCalls;

        private TrackingRandom(int nextInt, double nextDouble) {
            this.nextInt = nextInt;
            this.nextDouble = nextDouble;
        }

        @Override
        public int nextInt(int bound) {
            nextIntCalls++;
            return nextInt;
        }

        @Override
        public double nextDouble() {
            nextDoubleCalls++;
            return nextDouble;
        }
    }
}
