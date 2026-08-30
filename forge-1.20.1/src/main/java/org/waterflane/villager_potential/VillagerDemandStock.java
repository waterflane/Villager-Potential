package org.waterflane.villager_potential;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffer;
import org.waterflane.villager_potential.core.MarketDemandConfig;
import org.waterflane.villager_potential.core.MarketDemandStock;
import org.waterflane.villager_potential.core.MarketDemandStockConfig;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.Objects;

/** Applies bounded stock ceilings only after vanilla has completed a restock. */
public final class VillagerDemandStock {
    private VillagerDemandStock() {
    }

    public static void afterVanillaRestock(Villager villager) {
        Objects.requireNonNull(villager, "villager");
        if (villager.level().isClientSide()) {
            return;
        }

        MarketDemandStockConfig stockConfig = ServerConfig.marketDemandStockConfig();
        if (!stockConfig.enabled()) {
            for (MerchantOffer offer : villager.getOffers()) {
                resetToVanillaMaximum(offer);
            }
            return;
        }

        VillagerProfession minecraftProfession = villager.getVillagerData().getProfession();
        if (minecraftProfession == VillagerProfession.NONE
                || minecraftProfession == VillagerProfession.NITWIT) {
            return;
        }

        ProfessionId profession = VillagerProfessionIds.fromMinecraft(minecraftProfession);
        VillagerPotentialState state = VillagerPotentialAttachments.get(villager);
        long gameTime = villager.level().getGameTime();
        MarketDemandConfig demandConfig = Config.marketDemandConfig();
        for (MerchantOffer offer : villager.getOffers()) {
            double demandScore = state.marketDemandScoreFor(
                    profession,
                    MerchantOfferTradeKeys.from(offer),
                    gameTime,
                    demandConfig
            ).orElse(demandConfig.baseline());
            applyRestockedOffer(offer, demandScore, demandConfig, stockConfig);
        }
    }

    static void applyRestockedOffer(
            MerchantOffer offer,
            double demandScore,
            MarketDemandConfig demandConfig,
            MarketDemandStockConfig stockConfig
    ) {
        Objects.requireNonNull(offer, "offer");
        DemandStockOffer stockOffer = (DemandStockOffer) offer;
        if (offer.getUses() != 0) {
            stockOffer.villagerPotential$setEffectiveMaximumUses(
                    stockOffer.villagerPotential$baseMaximumUses()
            );
            return;
        }
        int maximumUses = MarketDemandStock.maximumUses(
                stockOffer.villagerPotential$baseMaximumUses(),
                demandScore,
                demandConfig,
                stockConfig
        );
        stockOffer.villagerPotential$setEffectiveMaximumUses(maximumUses);
    }

    private static void resetToVanillaMaximum(MerchantOffer offer) {
        DemandStockOffer stockOffer = (DemandStockOffer) offer;
        stockOffer.villagerPotential$setEffectiveMaximumUses(
                stockOffer.villagerPotential$baseMaximumUses()
        );
    }
}
