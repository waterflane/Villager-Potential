package org.waterflane.villager_potential.core;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AptitudeGeneratorTest {
    @Test
    void generatedValuesStayWithinConfiguredBounds() {
        AptitudeGenerationConfig config = new AptitudeGenerationConfig(
                0.5,
                1.5,
                1.0,
                4.0,
                0.05
        );
        Random random = new Random(912_345L);

        for (int sample = 0; sample < 10_000; sample++) {
            double aptitude = AptitudeGenerator.generate(config, random);

            assertTrue(aptitude >= config.minimum());
            assertTrue(aptitude <= config.maximum());
        }
    }

    @Test
    void distributionUsesConfiguredMeanAndVariance() {
        AptitudeGenerationConfig config = new AptitudeGenerationConfig(
                -100.0,
                100.0,
                2.5,
                0.36,
                0.0
        );
        Random random = new Random(42L);
        int sampleCount = 100_000;
        double sum = 0.0;
        double squaredSum = 0.0;

        for (int sample = 0; sample < sampleCount; sample++) {
            double aptitude = AptitudeGenerator.generate(config, random);
            sum += aptitude;
            squaredSum += aptitude * aptitude;
        }

        double observedMean = sum / sampleCount;
        double observedVariance = squaredSum / sampleCount - observedMean * observedMean;
        assertEquals(config.mean(), observedMean, 0.01);
        assertEquals(config.variance(), observedVariance, 0.01);
    }

    @Test
    void rareTalentPathCanProduceExceptionalAptitude() {
        AptitudeGenerationConfig ordinaryConfig = new AptitudeGenerationConfig(
                0.0,
                10.0,
                1.0,
                0.04,
                0.0
        );
        AptitudeGenerationConfig rareTalentConfig = new AptitudeGenerationConfig(
                0.0,
                10.0,
                1.0,
                0.04,
                1.0
        );

        double ordinary = AptitudeGenerator.generate(ordinaryConfig, new Random(123L));
        double rareTalent = AptitudeGenerator.generate(rareTalentConfig, new Random(123L));

        assertTrue(rareTalent >= ordinaryConfig.mean() + 3.0 * Math.sqrt(ordinaryConfig.variance()));
        assertTrue(rareTalent > ordinary);
    }

    @Test
    void fixedSeedProducesTheSameSequence() {
        AptitudeGenerationConfig config = new AptitudeGenerationConfig(
                0.5,
                2.0,
                1.0,
                0.09,
                0.02
        );
        Random firstRandom = new Random(867_5309L);
        Random secondRandom = new Random(867_5309L);
        Random differentRandom = new Random(867_5310L);

        for (int sample = 0; sample < 100; sample++) {
            double first = AptitudeGenerator.generate(config, firstRandom);
            double second = AptitudeGenerator.generate(config, secondRandom);
            double different = AptitudeGenerator.generate(config, differentRandom);

            assertEquals(first, second);
            if (sample == 0) {
                assertNotEquals(first, different);
            }
        }
    }
}
