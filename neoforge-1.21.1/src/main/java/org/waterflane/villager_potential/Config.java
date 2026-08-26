package org.waterflane.villager_potential;

import org.waterflane.villager_potential.core.MarketDemandConfig;
import org.waterflane.villager_potential.core.MarketDemandPriceConfig;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.SpecializationBiasConfig;
import org.waterflane.villager_potential.core.TradeKey;
import org.waterflane.villager_potential.core.TradeMemoryRecoveryConfig;
import org.waterflane.villager_potential.core.TradePaletteRerollStrategy;

/** Convenience accessors over the active validated server configuration. */
public final class Config {
    private Config() {
    }

    static SpecializationBiasConfig specializationBiasConfig(ProfessionId profession) {
        var skillConfig = ServerConfig.gameplayConfig().skill();
        return ServerConfig.tradeConfig().specializations().biasFor(
                profession,
                skillConfig.minimumSkill(),
                skillConfig.maximumSkill()
        );
    }

    public static int tradeHistoryMaximumEntries() {
        return ServerConfig.tradeConfig().palette().maximumHistoryEntries();
    }

    public static double seenTradeWeightMultiplier() {
        return ServerConfig.tradeConfig().palette().seenTradeWeightMultiplier();
    }

    public static TradeMemoryRecoveryConfig tradeMemoryRecoveryConfig() {
        return ServerConfig.tradeConfig().palette().recovery();
    }

    public static boolean isRareTradeProtected(TradeKey candidate) {
        return ServerConfig.tradeConfig().palette().isRareProtected(candidate);
    }

    public static TradePaletteRerollStrategy tradePaletteRerollStrategy() {
        return ServerConfig.tradeConfig().palette().mode();
    }

    public static MarketDemandConfig marketDemandConfig() {
        return ServerConfig.tradeConfig().economy().demand();
    }

    public static MarketDemandPriceConfig marketDemandPriceConfig() {
        return ServerConfig.tradeConfig().economy().price();
    }
}
