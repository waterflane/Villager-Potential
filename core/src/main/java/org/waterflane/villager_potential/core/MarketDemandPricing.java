package org.waterflane.villager_potential.core;

import java.util.Objects;

/** Pure demand-to-price calculations shared by platform integrations. */
public final class MarketDemandPricing {
    private MarketDemandPricing() {
    }

    /**
     * Maps the normal demand baseline to a neutral multiplier. Values on either
     * side interpolate toward the configured price bounds at the demand bounds.
     */
    public static double multiplier(
            double demandScore,
            MarketDemandConfig demandConfig,
            MarketDemandPriceConfig priceConfig
    ) {
        Objects.requireNonNull(demandConfig, "demandConfig");
        Objects.requireNonNull(priceConfig, "priceConfig");
        if (!Double.isFinite(demandScore)) {
            throw new IllegalArgumentException("demandScore must be finite");
        }
        if (!demandConfig.enabled() || !priceConfig.enabled()) {
            return 1.0;
        }

        double boundedDemand = clamp(
                demandScore,
                demandConfig.minimum(),
                demandConfig.maximum()
        );
        double baseline = demandConfig.baseline();
        if (boundedDemand >= baseline) {
            double range = demandConfig.maximum() - baseline;
            if (range == 0.0) {
                return 1.0;
            }
            double fraction = (boundedDemand - baseline) / range;
            return clamp(
                    1.0 + fraction * (priceConfig.maximumMultiplier() - 1.0),
                    priceConfig.minimumMultiplier(),
                    priceConfig.maximumMultiplier()
            );
        }

        double range = baseline - demandConfig.minimum();
        if (range == 0.0) {
            return 1.0;
        }
        double fraction = (baseline - boundedDemand) / range;
        return clamp(
                1.0 - fraction * (1.0 - priceConfig.minimumMultiplier()),
                priceConfig.minimumMultiplier(),
                priceConfig.maximumMultiplier()
        );
    }

    /**
     * Adds this mod's bounded base-price delta to a price already adjusted by
     * vanilla. Vanilla demand and special-price discounts are therefore retained.
     */
    public static int adjustedPrice(
            int vanillaPrice,
            int basePrice,
            int maximumItemCount,
            double demandScore,
            MarketDemandConfig demandConfig,
            MarketDemandPriceConfig priceConfig
    ) {
        if (maximumItemCount < 1) {
            throw new IllegalArgumentException("maximumItemCount must be positive");
        }

        int boundedVanillaPrice = clamp(vanillaPrice, 1, maximumItemCount);
        int boundedBasePrice = clamp(basePrice, 1, maximumItemCount);
        double priceMultiplier = multiplier(demandScore, demandConfig, priceConfig);
        double modDelta = Math.floor(boundedBasePrice * (priceMultiplier - 1.0));
        double adjusted = boundedVanillaPrice + modDelta;
        if (adjusted <= 1.0) {
            return 1;
        }
        if (adjusted >= maximumItemCount) {
            return maximumItemCount;
        }
        return (int) adjusted;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
