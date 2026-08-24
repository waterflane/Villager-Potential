package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AptitudeTierTest {
    private static final AptitudeGenerationConfig DEFAULT =
            VillagerPotentialConfig.DEFAULT.aptitude();

    @Test
    void defaultDistributionUsesStableHalfOpenTierBoundaries() {
        assertEquals(AptitudeTier.POOR, AptitudeTier.classify(0.5, DEFAULT));
        assertEquals(AptitudeTier.AVERAGE, AptitudeTier.classify(0.7, DEFAULT));
        assertEquals(AptitudeTier.AVERAGE, AptitudeTier.classify(1.299_999, DEFAULT));
        assertEquals(AptitudeTier.PROMISING, AptitudeTier.classify(1.3, DEFAULT));
        assertEquals(AptitudeTier.TALENTED, AptitudeTier.classify(1.6, DEFAULT));
        assertEquals(AptitudeTier.EXCEPTIONAL, AptitudeTier.classify(1.9, DEFAULT));
        assertEquals(AptitudeTier.EXCEPTIONAL, AptitudeTier.classify(2.0, DEFAULT));
    }

    @Test
    void customRangeAndVarianceMoveTierBoundaries() {
        AptitudeGenerationConfig config = new AptitudeGenerationConfig(
                true,
                2.0,
                8.0,
                4.0,
                1.0,
                new RareTalentConfig(true, 0.1, 3.0)
        );

        assertEquals(AptitudeTier.POOR, AptitudeTier.classify(2.99, config));
        assertEquals(AptitudeTier.AVERAGE, AptitudeTier.classify(3.0, config));
        assertEquals(AptitudeTier.PROMISING, AptitudeTier.classify(5.0, config));
        assertEquals(AptitudeTier.TALENTED, AptitudeTier.classify(6.0, config));
        assertEquals(AptitudeTier.EXCEPTIONAL, AptitudeTier.classify(7.0, config));
    }

    @Test
    void zeroVarianceStillTreatsTheConfiguredMeanAsAverage() {
        AptitudeGenerationConfig config = new AptitudeGenerationConfig(
                true,
                0.5,
                2.0,
                1.0,
                0.0,
                new RareTalentConfig(false, 0.0, 3.0)
        );

        assertEquals(AptitudeTier.AVERAGE, AptitudeTier.classify(1.0, config));
        assertThrows(IllegalArgumentException.class,
                () -> AptitudeTier.classify(Double.NaN, config));
    }

    @Test
    void displayModesKeepExactValuesBehindExplicitOptIn() {
        assertEquals(false, AptitudeDisplayMode.DISABLED.visible());
        assertEquals(false, AptitudeDisplayMode.DISABLED.exactValueVisible());
        assertEquals(true, AptitudeDisplayMode.QUALITATIVE.visible());
        assertEquals(false, AptitudeDisplayMode.QUALITATIVE.exactValueVisible());
        assertEquals(true, AptitudeDisplayMode.EXACT.exactValueVisible());
    }
}
