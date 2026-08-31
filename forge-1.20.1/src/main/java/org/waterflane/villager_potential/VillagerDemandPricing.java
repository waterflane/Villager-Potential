package org.waterflane.villager_potential;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
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
            apply(villager, offer, profession, state, gameTime, demandConfig, priceConfig);
        }
    }

    /** Reprices only the offer completed by the current trade action. */
    public static void apply(Villager villager, MerchantOffer offer) {
        Objects.requireNonNull(villager, "villager");
        Objects.requireNonNull(offer, "offer");
        VillagerProfession minecraftProfession = villager.getVillagerData().getProfession();
        if (villager.level().isClientSide()
                || minecraftProfession == VillagerProfession.NONE
                || minecraftProfession == VillagerProfession.NITWIT) {
            return;
        }
        apply(
                villager,
                offer,
                VillagerProfessionIds.fromMinecraft(minecraftProfession),
                VillagerPotentialAttachments.get(villager),
                villager.level().getGameTime(),
                Config.marketDemandConfig(),
                Config.marketDemandPriceConfig()
        );
    }

    /** Sends current offers after the result slot has completed the old-price purchase. */
    public static void syncOpenMenu(Villager villager) {
        Player player = villager.getTradingPlayer();
        if (player == null || villager.getOffers().isEmpty()) {
            return;
        }
        player.sendMerchantOffers(
                player.containerMenu.containerId,
                villager.getOffers(),
                villager.getVillagerData().getLevel(),
                villager.getVillagerXp(),
                villager.showProgressBar(),
                villager.canRestock()
        );
    }

    private static void apply(
            Villager villager,
            MerchantOffer offer,
            ProfessionId profession,
            VillagerPotentialState state,
            long gameTime,
            MarketDemandConfig demandConfig,
            MarketDemandPriceConfig priceConfig
    ) {
        DemandPriceOffer demandPriceOffer = (DemandPriceOffer) offer;
        demandPriceOffer.villagerPotential$clearDemandPriceAdjustment();
        MerchantOfferTradeKeys.Identity identity = MerchantOfferTradeKeys.identify(offer);
        OptionalDouble demandScore = identity.stable()
                ? state.marketDemandScoreFor(
                profession,
                identity.key(),
                gameTime,
                demandConfig
        )
                : OptionalDouble.empty();
        double score = demandScore.orElse(demandConfig.baseline());
        int vanillaPrice = offer.getCostA().getCount();
        int adjustedPrice = apply(offer, score, demandConfig, priceConfig);
        VillagerPotentialDiagnostics.price(
                villager.getUUID(),
                profession,
                identity.key(),
                score,
                vanillaPrice,
                adjustedPrice
        );
    }

    static int apply(
            MerchantOffer offer,
            double demandScore,
            MarketDemandConfig demandConfig,
            MarketDemandPriceConfig priceConfig
    ) {
        Objects.requireNonNull(offer, "offer");
        DemandPriceOffer demandPriceOffer = (DemandPriceOffer) offer;
        demandPriceOffer.villagerPotential$clearDemandPriceAdjustment();
        int vanillaPrice = offer.getCostA().getCount();
        int basePrice = offer.getBaseCostA().getCount();
        int maximumItemCount = offer.getBaseCostA().getMaxStackSize();
        MarketDemandPricing.PaymentKind paymentKind = offer.getBaseCostA().is(Items.EMERALD)
                ? MarketDemandPricing.PaymentKind.EMERALD
                : MarketDemandPricing.PaymentKind.OTHER_ITEM;
        int demandNeutralPrice = paymentKind == MarketDemandPricing.PaymentKind.EMERALD
                ? MarketDemandPricing.specialPriceWithoutDemand(
                        basePrice,
                        offer.getSpecialPriceDiff(),
                        maximumItemCount
                )
                : vanillaPrice;
        MarketDemandPricing.OfferAdjustment adjustment = MarketDemandPricing.adjustedOffer(
                demandNeutralPrice,
                basePrice,
                maximumItemCount,
                demandPriceOffer.villagerPotential$baseResultCount(),
                paymentKind,
                demandScore,
                demandConfig,
                priceConfig
        );
        int adjustedPrice = adjustment.inputPrice();
        if (paymentKind == MarketDemandPricing.PaymentKind.OTHER_ITEM
                && demandConfig.enabled()
                && priceConfig.enabled()) {
            adjustedPrice = demandPriceOffer.villagerPotential$retainDemandInputPrice(
                    adjustedPrice,
                    MarketDemandPricing.maximumItemPaymentPrice(
                            basePrice,
                            maximumItemCount,
                            priceConfig
                    ),
                    demandScore > demandConfig.baseline()
            );
        } else {
            demandPriceOffer.villagerPotential$clearDemandInputPriceFloor();
        }
        demandPriceOffer.villagerPotential$applyDemandInputDelta(
                adjustedPrice - vanillaPrice
        );
        demandPriceOffer.villagerPotential$setEffectiveResultCount(
                adjustment.resultCount()
        );
        return adjustedPrice;
    }

    /** Restores demand-controlled offer values after completed sleep. */
    public static void resetAfterSleep(Villager villager) {
        Objects.requireNonNull(villager, "villager");
        for (MerchantOffer offer : villager.getOffers()) {
            ((DemandPriceOffer) offer).villagerPotential$resetDemandPrice();
        }
    }
}
