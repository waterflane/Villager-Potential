package org.waterflane.villager_potential.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Persistent, platform-independent trade selection state for one profession.
 *
 * <p>Only portable trade identities, aggregate observations, and the number
 * of uses in the current restock cycle are retained. Merchant-offer instances,
 * prices, demand, and other Minecraft runtime state deliberately remain outside
 * this record.</p>
 */
public record TradePaletteState(
        List<TradeKey> activeTrades,
        Map<TradeKey, TradeHistory> offerHistory,
        Map<TradeKey, Integer> usesSinceRestock
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

        Objects.requireNonNull(usesSinceRestock, "usesSinceRestock");
        usesSinceRestock.forEach((trade, uses) -> {
            Objects.requireNonNull(trade, "restockTrade");
            Objects.requireNonNull(uses, "restockUses");
            if (uses < 1) {
                throw new IllegalArgumentException("restock uses must be positive");
            }
        });
        usesSinceRestock = Map.copyOf(usesSinceRestock);
    }

    public TradePaletteState(
            List<TradeKey> activeTrades,
            Map<TradeKey, TradeHistory> offerHistory
    ) {
        this(activeTrades, offerHistory, Map.of());
    }

    /**
     * Migrates the version-seven occurrence list into aggregate counts. Old
     * saves did not retain timestamps, so their last-seen values remain empty.
     */
    public TradePaletteState(List<TradeKey> activeTrades, List<TradeKey> selectionHistory) {
        this(activeTrades, aggregateLegacyHistory(selectionHistory), Map.of());
    }

    public static TradePaletteState empty() {
        return new TradePaletteState(List.of(), Map.of(), Map.of());
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
        if (newlyGeneratedTrades.isEmpty()) {
            return this;
        }

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
            for (TradeKey trade : newlyGeneratedTrades) {
                if (appended.size() >= maximumHistoryEntries) {
                    break;
                }
                if (!appended.contains(trade)) {
                    appended.add(trade);
                }
            }
            learnedTrades = List.copyOf(appended);
        }
        Map<TradeKey, Integer> retainedUses = retainKnownUses(updatedHistory);
        return new TradePaletteState(learnedTrades, updatedHistory, retainedUses);
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
        Map<TradeKey, Integer> updatedUses = new HashMap<>(usesSinceRestock);
        updatedUses.compute(trade, (ignored, uses) -> increment(uses == null ? 0 : uses));
        updatedUses.keySet().retainAll(updatedHistory.keySet());
        return new TradePaletteState(activeTrades, updatedHistory, updatedUses);
    }

    /** Resets only CYCLIC seen counts; learned trades and use history are retained. */
    public TradePaletteState resetSeenCounts(Set<TradeKey> trades) {
        Objects.requireNonNull(trades, "trades");
        if (trades.isEmpty()) {
            return this;
        }
        Map<TradeKey, TradeHistory> updatedHistory = new HashMap<>(offerHistory);
        trades.forEach(trade -> updatedHistory.computeIfPresent(
                Objects.requireNonNull(trade, "trade"),
                (ignored, history) -> history.resetSeenCount()
        ));
        return updatedHistory.equals(offerHistory)
                ? this
                : new TradePaletteState(activeTrades, updatedHistory, usesSinceRestock);
    }

    /** Clears stock usage only after a completed villager sleep/restock cycle. */
    public TradePaletteState resetRestockUses() {
        return usesSinceRestock.isEmpty()
                ? this
                : new TradePaletteState(activeTrades, offerHistory, Map.of());
    }

    /**
     * Newest seen-observation in a whole offer history, or {@code 0L} when
     * nothing was ever seen. Callers that only need one memory time base for
     * an entire palette can use this instead of re-walking the map.
     */
    public static long latestSeenTime(Map<TradeKey, TradeHistory> offerHistory) {
        Objects.requireNonNull(offerHistory, "offerHistory");
        return offerHistory.values().stream()
                .filter(history -> history.lastSeen().isPresent())
                .mapToLong(history -> history.lastSeen().getAsLong())
                .max()
                .orElse(0L);
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

    private static int increment(int value) {
        return value == Integer.MAX_VALUE ? Integer.MAX_VALUE : value + 1;
    }

    private Map<TradeKey, Integer> retainKnownUses(
            Map<TradeKey, TradeHistory> updatedHistory
    ) {
        if (usesSinceRestock.isEmpty()) {
            return Map.of();
        }
        Map<TradeKey, Integer> retained = new HashMap<>(usesSinceRestock);
        retained.keySet().retainAll(updatedHistory.keySet());
        return retained;
    }
}
