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
            int adjustedPrice = apply(
                    offer,
                    score,
                    demandConfig,
                    priceConfig
            );
            VillagerPotentialDiagnostics.price(
                    villager.getUUID(),
                    profession,
                    identity.key(),
                    score,
                    vanillaPrice,
                    adjustedPrice
            );
        }
    }

    /** Applies the new price immediately and refreshes an already open trade menu. */
    public static void applyAndSync(Villager villager) {
        apply(villager);
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
        MarketDemandPricing.OfferAdjustment adjustment = MarketDemandPricing.adjustedOffer(
                vanillaPrice,
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
