package org.waterflane.villager_potential.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One named specialization and its multiplicative trade-category weights.
 * Categories not present in the map retain the neutral modifier of {@code 1.0}.
 */
public record SpecializationDefinition(
        SpecializationId id,
        Map<TradeCategoryId, Double> tradeCategoryWeightModifiers
) {
    public SpecializationDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tradeCategoryWeightModifiers, "tradeCategoryWeightModifiers");

        LinkedHashMap<TradeCategoryId, Double> weights = new LinkedHashMap<>();
        tradeCategoryWeightModifiers.forEach((categoryId, weight) -> {
            Objects.requireNonNull(categoryId, "tradeCategoryWeightModifiers must not contain a null category");
            Objects.requireNonNull(weight, "tradeCategoryWeightModifiers must not contain a null weight");
            if (!Double.isFinite(weight) || weight < 0.0) {
                throw new IllegalArgumentException(
                        "Trade category weight for " + categoryId + " must be finite and non-negative: " + weight
                );
            }
            weights.put(categoryId, weight);
        });
        tradeCategoryWeightModifiers = Map.copyOf(weights);
    }

    public double weightModifierFor(TradeCategoryId categoryId) {
        Objects.requireNonNull(categoryId, "categoryId");
        return tradeCategoryWeightModifiers.getOrDefault(categoryId, 1.0);
    }
}
