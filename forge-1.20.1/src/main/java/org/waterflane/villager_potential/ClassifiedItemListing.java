package org.waterflane.villager_potential;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffer;
import org.waterflane.villager_potential.core.TradeCategoryId;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Classification metadata around an unchanged trade factory.
 *
 * <p>The delegate remains responsible for materializing the offer, including
 * registry-dependent enchantments, villager-type choices, and null results.
 */
public final class ClassifiedItemListing implements VillagerTrades.ItemListing {
    private final VillagerTrades.ItemListing delegate;
    private final TradeCategoryId category;

    public ClassifiedItemListing(VillagerTrades.ItemListing delegate, TradeCategoryId category) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.category = Objects.requireNonNull(category, "category");
    }

    public VillagerTrades.ItemListing delegate() {
        return delegate;
    }

    public TradeCategoryId category() {
        return category;
    }

    @Override
    @Nullable
    public MerchantOffer getOffer(Entity entity, RandomSource random) {
        return delegate.getOffer(entity, random);
    }
}
