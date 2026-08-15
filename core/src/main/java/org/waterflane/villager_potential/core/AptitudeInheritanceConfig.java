package org.waterflane.villager_potential.core;

/**
 * Platform-independent parameters for aptitude inheritance.
 *
 * <p>The inheritance and random contributions are weights. Any weight left over
 * is assigned to the configured aptitude-generation mean, which provides a
 * neutral baseline. Mutation variance is the variance of a zero-mean Gaussian
 * deviation applied after those contributions are combined.</p>
 */
public record AptitudeInheritanceConfig(
        double inheritanceStrength,
        double randomContribution,
        double mutationVariance
) {
    public AptitudeInheritanceConfig {
        requireFinite("inheritanceStrength", inheritanceStrength);
        requireFinite("randomContribution", randomContribution);
        requireFinite("mutationVariance", mutationVariance);

        if (inheritanceStrength < 0.0 || inheritanceStrength > 1.0) {
            throw new IllegalArgumentException("inheritanceStrength must be between zero and one");
        }
        if (randomContribution < 0.0 || randomContribution > 1.0) {
            throw new IllegalArgumentException("randomContribution must be between zero and one");
        }
        if (inheritanceStrength + randomContribution > 1.0) {
            throw new IllegalArgumentException(
                    "inheritanceStrength and randomContribution must not total more than one"
            );
        }
        if (mutationVariance < 0.0) {
            throw new IllegalArgumentException("mutationVariance must not be negative");
        }
    }

    private static void requireFinite(String name, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
