package org.waterflane.villager_potential;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffer;
import org.waterflane.villager_potential.core.MarketDemandConfig;
import org.waterflane.villager_potential.core.MarketDemandPriceConfig;
import org.waterflane.villager_potential.core.MarketDemandPricing;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.VillagerPotentialState;

import java.util.Objects;
import java.util.OptionalDouble;

/** Adds Potential's demand delta after vanilla has prepared special prices. */
public final class VillagerDemandPricing {
    private VillagerDemandPricing() {
    }

    public static void apply(Villager villager) {
        Objects.requireNonNull(villager, "villager");
        if (villager.level().isClientSide()) {
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
        MarketDemandPriceConfig priceConfig = Config.marketDemandPriceConfig();

        for (MerchantOffer offer : villager.getOffers()) {
            OptionalDouble demandScore = state.marketDemandScoreFor(
                    profession,
                    MerchantOfferTradeKeys.from(offer),
                    gameTime,
                    demandConfig
            );
            apply(
                    offer,
                    demandScore.orElse(demandConfig.baseline()),
                    demandConfig,
                    priceConfig
            );
        }
    }

    static void apply(
            MerchantOffer offer,
            double demandScore,
            MarketDemandConfig demandConfig,
            MarketDemandPriceConfig priceConfig
    ) {
        Objects.requireNonNull(offer, "offer");
        int vanillaPrice = offer.getCostA().getCount();
        int basePrice = offer.getBaseCostA().getCount();
        int maximumItemCount = offer.getBaseCostA().getMaxStackSize();
        int adjustedPrice = MarketDemandPricing.adjustedPrice(
                vanillaPrice,
                basePrice,
                maximumItemCount,
                demandScore,
                demandConfig,
                priceConfig
        );
        offer.addToSpecialPriceDiff(adjustedPrice - vanillaPrice);
    }
}
