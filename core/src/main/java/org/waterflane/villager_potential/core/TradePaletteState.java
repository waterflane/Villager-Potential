package org.waterflane.villager_potential.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Persistent, platform-independent trade selection state for one profession.
 *
 * <p>Only portable trade identities and aggregate observations are retained.
 * Merchant-offer instances, prices, demand, and other Minecraft runtime state
 * deliberately remain outside this record.</p>
 */
public record TradePaletteState(
        List<TradeKey> activeTrades,
        Map<TradeKey, TradeHistory> offerHistory
) {
    public TradePaletteState {
        Objects.requireNonNull(activeTrades, "activeTrades");
        activeTrades.forEach(trade -> Objects.requireNonNull(trade, "activeTrade"));
        activeTrades = List.copyOf(activeTrades);

        Objects.requireNonNull(offerHistory, "offerHistory");
        offerHistory.forEach((trade, history) -> {
            Objects.requireNonNull(trade, "historyTrade");
            Objects.requireNonNull(history, "tradeHistory");
        });
        offerHistory = Map.copyOf(offerHistory);
    }

    /**
     * Migrates the version-seven occurrence list into aggregate counts. Old
     * saves did not retain timestamps, so their last-seen values remain empty.
     */
    public TradePaletteState(List<TradeKey> activeTrades, List<TradeKey> selectionHistory) {
        this(activeTrades, aggregateLegacyHistory(selectionHistory));
    }

    public static TradePaletteState empty() {
        return new TradePaletteState(List.of(), Map.of());
    }

    public TradePaletteState recordPresented(
            List<TradeKey> presentedTrades,
            List<TradeKey> newlyGeneratedTrades,
            long gameTime,
            int maximumHistoryEntries
    ) {
        return recordPresented(
                presentedTrades,
                newlyGeneratedTrades,
                gameTime,
                maximumHistoryEntries,
                TradePaletteRerollStrategy.PERSISTENT
        );
    }

    public TradePaletteState recordPresented(
            List<TradeKey> presentedTrades,
            List<TradeKey> newlyGeneratedTrades,
            long gameTime,
            int maximumHistoryEntries,
            TradePaletteRerollStrategy strategy
    ) {
        Objects.requireNonNull(presentedTrades, "presentedTrades");
        Objects.requireNonNull(newlyGeneratedTrades, "newlyGeneratedTrades");
        Objects.requireNonNull(strategy, "strategy");
        validateMaximumHistoryEntries(maximumHistoryEntries);

        Map<TradeKey, TradeHistory> updatedHistory = new HashMap<>(offerHistory);
        for (TradeKey trade : newlyGeneratedTrades) {
            Objects.requireNonNull(trade, "newlyGeneratedTrade");
            updatedHistory.compute(
                    trade,
                    (ignored, history) -> history == null
                            ? TradeHistory.seenAt(gameTime)
                            : history.recordSeen(gameTime)
            );
            pruneToCapacity(updatedHistory, trade, maximumHistoryEntries);
        }
        List<TradeKey> learnedTrades = activeTrades;
        if (strategy == TradePaletteRerollStrategy.PERSISTENT) {
            List<TradeKey> appended = new ArrayList<>(activeTrades);
            newlyGeneratedTrades.stream()
                    .filter(trade -> !appended.contains(trade))
                    .forEach(appended::add);
            learnedTrades = List.copyOf(appended);
        }
        return new TradePaletteState(learnedTrades, updatedHistory);
    }

    public TradePaletteState recordUsed(
            TradeKey trade,
            long gameTime,
            int maximumHistoryEntries
    ) {
        Objects.requireNonNull(trade, "trade");
        validateMaximumHistoryEntries(maximumHistoryEntries);
        Map<TradeKey, TradeHistory> updatedHistory = new HashMap<>(offerHistory);
        updatedHistory.compute(
                trade,
                (ignored, history) -> history == null
                        ? TradeHistory.empty().recordUsed(gameTime)
                        : history.recordUsed(gameTime)
        );
        pruneToCapacity(updatedHistory, trade, maximumHistoryEntries);
        return new TradePaletteState(activeTrades, updatedHistory);
    }

    private static Map<TradeKey, TradeHistory> aggregateLegacyHistory(
            List<TradeKey> selectionHistory
    ) {
        Objects.requireNonNull(selectionHistory, "selectionHistory");
        Map<TradeKey, TradeHistory> history = new HashMap<>();
        for (TradeKey trade : selectionHistory) {
            Objects.requireNonNull(trade, "selectionHistoryTrade");
            history.compute(
                    trade,
                    (ignored, current) -> new TradeHistory(
                            current == null ? 1L : increment(current.timesSeen()),
                            java.util.OptionalLong.empty(),
                            0L,
                            java.util.OptionalLong.empty()
                    )
            );
        }
        return history;
    }

    private static void pruneToCapacity(
            Map<TradeKey, TradeHistory> history,
            TradeKey retained,
            int maximumHistoryEntries
    ) {
        while (history.size() > maximumHistoryEntries) {
            TradeKey oldest = null;
            long oldestTime = Long.MAX_VALUE;
            for (Map.Entry<TradeKey, TradeHistory> entry : history.entrySet()) {
                if (entry.getKey().equals(retained)) {
                    continue;
                }
                long observationTime = entry.getValue().mostRecentObservation();
                if (oldest == null || observationTime < oldestTime) {
                    oldest = entry.getKey();
                    oldestTime = observationTime;
                }
            }
            history.remove(oldest);
        }
    }

    private static void validateMaximumHistoryEntries(int maximumHistoryEntries) {
        if (maximumHistoryEntries < 1) {
            throw new IllegalArgumentException("maximumHistoryEntries must be positive");
        }
    }

    private static long increment(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1L;
    }
}
