package org.waterflane.villager_potential;

/** Server-thread context identifying trades executed inside result-slot Shift-click. */
public final class BulkTradeContext {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private BulkTradeContext() {
    }

    public static void begin() {
        DEPTH.set(DEPTH.get() + 1);
    }

    public static void end() {
        int depth = DEPTH.get() - 1;
        if (depth <= 0) {
            DEPTH.remove();
        } else {
            DEPTH.set(depth);
        }
    }

    public static boolean active() {
        return DEPTH.get() > 0;
    }
}
