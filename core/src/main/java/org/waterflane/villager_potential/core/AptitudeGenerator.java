package org.waterflane.villager_potential.core;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Generates bounded aptitude values without depending on a game platform.
 */
public final class AptitudeGenerator {
    private static final double RARE_TALENT_STANDARD_DEVIATIONS = 3.0;
    private static final int MAX_SAMPLE_ATTEMPTS = 100;

    private AptitudeGenerator() {
    }

    public static double generate(AptitudeGenerationConfig config, RandomGenerator random) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(random, "random");

        if (config.variance() == 0.0) {
            return config.mean();
        }

        boolean rareTalent = random.nextDouble() < config.rareTalentChance();
        double standardDeviation = Math.sqrt(config.variance());
        double lastSample = config.mean();

        for (int attempt = 0; attempt < MAX_SAMPLE_ATTEMPTS; attempt++) {
            double deviation = random.nextGaussian();
            if (rareTalent) {
                deviation = RARE_TALENT_STANDARD_DEVIATIONS + Math.abs(deviation);
            }

            lastSample = config.mean() + standardDeviation * deviation;
            if (lastSample >= config.minimum() && lastSample <= config.maximum()) {
                return lastSample;
            }
        }

        return Math.max(config.minimum(), Math.min(config.maximum(), lastSample));
    }
}
