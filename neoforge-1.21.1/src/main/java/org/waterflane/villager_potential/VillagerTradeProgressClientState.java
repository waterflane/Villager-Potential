package org.waterflane.villager_potential;

import java.util.Optional;

/** Client-side mailbox kept free of client-only class references for server safety. */
public final class VillagerTradeProgressClientState {
    private static VillagerTradeProgressPayload latest;

    private VillagerTradeProgressClientState() {
    }

    public static void accept(VillagerTradeProgressPayload payload) {
        latest = payload;
    }

    public static Optional<VillagerTradeProgressPayload> latest() {
        return Optional.ofNullable(latest);
    }

    public static void clear() {
        latest = null;
    }
}
