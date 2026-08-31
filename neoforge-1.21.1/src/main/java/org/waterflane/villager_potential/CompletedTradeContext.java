package org.waterflane.villager_potential;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffer;

/** Carries the completed event offer to the end of the current result-slot action. */
public final class CompletedTradeContext {
    private static final ThreadLocal<CompletedTrade> COMPLETED = new ThreadLocal<>();

    private CompletedTradeContext() {
    }

    public static void record(Villager villager, MerchantOffer offer) {
        COMPLETED.set(new CompletedTrade(villager, offer));
    }

    public static MerchantOffer takeFor(Villager villager) {
        CompletedTrade completed = COMPLETED.get();
        COMPLETED.remove();
        return completed != null && completed.villager() == villager
                ? completed.offer()
                : null;
    }

    private record CompletedTrade(Villager villager, MerchantOffer offer) {
    }
}
