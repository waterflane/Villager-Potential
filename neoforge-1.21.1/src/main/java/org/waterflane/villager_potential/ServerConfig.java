package org.waterflane.villager_potential;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.waterflane.villager_potential.core.MarketDemandStockConfig;

/** World/server-owned settings for behavior that changes villager stock. */
public final class ServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    static final MarketDemandStockConfig DEFAULT_MARKET_DEMAND_STOCK =
            MarketDemandStockConfig.DISABLED;

    private static final ModConfigSpec.BooleanValue DEMAND_INFLUENCES_STOCK = BUILDER
            .comment(
                    "Allow Potential market demand to add uses after a vanilla-approved villager restock.",
                    "This never creates extra restocks or bypasses workstation and daily timing checks."
            )
            .define("marketDemand.stock.enabled", DEFAULT_MARKET_DEMAND_STOCK.enabled());
    private static final ModConfigSpec.IntValue MAXIMUM_ADDITIONAL_USES = BUILDER
            .comment("Maximum uses demand may add to one offer per restock.")
            .defineInRange(
                    "marketDemand.stock.maximumAdditionalUses",
                    DEFAULT_MARKET_DEMAND_STOCK.maximumAdditionalUses(),
                    0,
                    64
            );
    private static final ModConfigSpec.IntValue MAXIMUM_USES_PER_OFFER = BUILDER
            .comment(
                    "Hard ceiling above which demand will not raise an offer's total uses.",
                    "Existing vanilla or modded offers above this value are never reduced."
            )
            .defineInRange(
                    "marketDemand.stock.maximumUsesPerOffer",
                    DEFAULT_MARKET_DEMAND_STOCK.maximumUsesPerOffer(),
                    1,
                    64
            );

    static final ModConfigSpec SPEC = BUILDER.build();

    private ServerConfig() {
    }

    public static MarketDemandStockConfig marketDemandStockConfig() {
        return new MarketDemandStockConfig(
                DEMAND_INFLUENCES_STOCK.get(),
                MAXIMUM_ADDITIONAL_USES.get(),
                MAXIMUM_USES_PER_OFFER.get()
        );
    }
}
