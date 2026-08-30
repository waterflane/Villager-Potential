package org.waterflane.villager_potential.core;

import java.util.Objects;

/** Pure demand-to-price calculations shared by platform integrations. */
public final class MarketDemandPricing {
    private static final double ROUNDING_EPSILON = 1.0E-9;

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

    /**
     * Resolves both sides of one offer without ever changing an emerald stack.
     * Emerald payments make the received product stack smaller; other payments
     * make the paid product stack larger.
     */
    public static OfferAdjustment adjustedOffer(
            int vanillaPrice,
            int basePrice,
            int maximumItemCount,
            int baseResultCount,
            PaymentKind paymentKind,
            double demandScore,
            MarketDemandConfig demandConfig,
            MarketDemandPriceConfig priceConfig
    ) {
        Objects.requireNonNull(paymentKind, "paymentKind");
        if (maximumItemCount < 1 || baseResultCount < 1) {
            throw new IllegalArgumentException("offer counts and limits must be positive");
        }

        int boundedVanillaPrice = clamp(vanillaPrice, 1, maximumItemCount);
        int boundedBasePrice = clamp(basePrice, 1, maximumItemCount);
        double pressure = positiveDemandFraction(demandScore, demandConfig, priceConfig);
        int inputPrice = boundedVanillaPrice;
        int resultCount = baseResultCount;
        if (paymentKind == PaymentKind.EMERALD) {
            double maximumReduction = priceConfig.maximumEmeraldPaymentResultReduction();
            resultCount = Math.max(
                    1,
                    (int) Math.ceil(
                            baseResultCount * (1.0 - maximumReduction * pressure)
                                    - ROUNDING_EPSILON
                    )
            );
        } else {
            double maximumIncrease = priceConfig.maximumItemPaymentIncrease();
            int addedItems = (int) Math.floor(
                    boundedBasePrice * maximumIncrease * pressure + ROUNDING_EPSILON
            );
            int configuredMaximum = Math.max(
                    boundedBasePrice,
                    (int) Math.floor(
                            boundedBasePrice * (1.0 + maximumIncrease) + ROUNDING_EPSILON
                    )
            );
            int startingPrice = pressure > 0.0
                    ? Math.max(boundedVanillaPrice, boundedBasePrice)
                    : boundedVanillaPrice;
            inputPrice = startingPrice >= configuredMaximum
                    ? startingPrice
                    : clamp(
                            Math.min(configuredMaximum, startingPrice + addedItems),
                            1,
                            maximumItemCount
                    );
        }
        return new OfferAdjustment(inputPrice, resultCount);
    }

    private static double positiveDemandFraction(
            double demandScore,
            MarketDemandConfig demandConfig,
            MarketDemandPriceConfig priceConfig
    ) {
        if (!demandConfig.enabled() || !priceConfig.enabled()) {
            return 0.0;
        }
        double boundedDemand = clamp(
                demandScore,
                demandConfig.minimum(),
                demandConfig.maximum()
        );
        double demandAboveBaseline = boundedDemand - demandConfig.baseline();
        if (demandAboveBaseline <= 0.0) {
            return 0.0;
        }
        double availableRange = demandConfig.maximum() - demandConfig.baseline();
        double saturationRange = Math.min(
                availableRange,
                priceConfig.demandScoreForMaximumPrice()
        );
        if (saturationRange <= 0.0) {
            return 0.0;
        }
        double normalizedDemand = demandAboveBaseline / saturationRange;
        double curveStrength = priceConfig.maximumMultiplier() - 1.0;
        return clamp(normalizedDemand * curveStrength, 0.0, 1.0);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record OfferAdjustment(int inputPrice, int resultCount) {
        public OfferAdjustment {
            if (inputPrice < 1 || resultCount < 1) {
                throw new IllegalArgumentException("offer counts must be positive");
            }
        }
    }

    public enum PaymentKind {
        EMERALD,
        OTHER_ITEM
    }
}
