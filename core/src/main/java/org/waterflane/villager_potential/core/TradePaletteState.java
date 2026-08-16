package org.waterflane.villager_potential.core;

import java.util.List;
import java.util.Objects;

/**
 * Persistent, platform-independent trade selection state for one profession.
 *
 * <p>Only portable trade identities are retained. The currently displayed
 * merchant offers, their usage, demand, prices, and other Minecraft runtime
 * state deliberately remain outside this record.</p>
 */
public record TradePaletteState(
        List<TradeKey> activeTrades,
        List<TradeKey> selectionHistory
) {
    public TradePaletteState {
        Objects.requireNonNull(activeTrades, "activeTrades");
        activeTrades.forEach(trade -> Objects.requireNonNull(trade, "activeTrade"));
        activeTrades = List.copyOf(activeTrades);

        Objects.requireNonNull(selectionHistory, "selectionHistory");
        selectionHistory.forEach(trade -> Objects.requireNonNull(trade, "selectionHistoryTrade"));
        selectionHistory = List.copyOf(selectionHistory);
    }

    public static TradePaletteState empty() {
        return new TradePaletteState(List.of(), List.of());
    }
}
