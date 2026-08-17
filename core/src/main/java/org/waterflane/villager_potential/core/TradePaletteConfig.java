package org.waterflane.villager_potential.core;

import java.util.Objects;
import java.util.Set;

/** Loader-neutral palette and reroll-memory policy. */
public record TradePaletteConfig(
        TradePaletteRerollStrategy mode,
        int maximumHistoryEntries,
        double repeatedTradePenalty,
        TradeMemoryRecoveryConfig recovery,
        boolean rareTradeProtectionEnabled,
        Set<String> rareTradeResultItems
) {
    public TradePaletteConfig {
        Objects.requireNonNull(mode, "mode");
        if (maximumHistoryEntries < 1) {
            throw new IllegalArgumentException("maximumHistoryEntries must be positive");
        }
        if (!Double.isFinite(repeatedTradePenalty)
                || repeatedTradePenalty < 0.0
                || repeatedTradePenalty > 1.0) {
            throw new IllegalArgumentException(
                    "repeatedTradePenalty must be between zero and one"
            );
        }
        Objects.requireNonNull(recovery, "recovery");
        Objects.requireNonNull(rareTradeResultItems, "rareTradeResultItems");
        rareTradeResultItems.forEach(item -> Objects.requireNonNull(item, "rare trade item"));
        rareTradeResultItems = Set.copyOf(rareTradeResultItems);
    }

    public double seenTradeWeightMultiplier() {
        return 1.0 - repeatedTradePenalty;
    }

    public boolean isRareProtected(TradeKey trade) {
        return rareTradeProtectionEnabled
                && trade instanceof TradeKey.Offer offer
                && rareTradeResultItems.contains(offer.result().itemId());
    }
}
