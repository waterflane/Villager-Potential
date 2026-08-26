package org.waterflane.villager_potential;

import com.mojang.serialization.DataResult;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import org.waterflane.villager_potential.core.TradeKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds portable logical identities from generated Minecraft trades.
 */
public final class MerchantOfferTradeKeys {
    private static final String REMOVED = "removed";

    private MerchantOfferTradeKeys() {
    }

    /**
     * Builds a key from the generated offer. The base first cost is used so
     * demand and temporary reputation discounts do not affect identity.
     */
    public static TradeKey from(MerchantOffer offer) {
        return identify(offer).key();
    }

    /**
     * Identifies an offer and reports whether the key is safe for durable
     * memory/demand. An unstable fallback is still returned so foreign offers
     * can pass through hooks without being rejected.
     */
    public static Identity identify(MerchantOffer offer) {
        Objects.requireNonNull(offer, "offer");
        try {
            TradeKey.Item costA = item(offer.getBaseCostA());
            ItemStack costBStack = offer.getCostB();
            Optional<TradeKey.Item> costB = costBStack.isEmpty()
                    ? Optional.empty()
                    : Optional.of(item(costBStack));
            TradeKey key = new TradeKey.Offer(costA, costB, item(offer.getResult()));
            return new Identity(key, isStable(key));
        } catch (RuntimeException exception) {
            return new Identity(fallbackFor(offer), false);
        }
    }

    public static boolean isStable(TradeKey key) {
        return TradeKey.isStable(key);
    }

    /**
     * Generates a concrete candidate once and returns its logical identity.
     * A candidate that declines to create an offer has no key.
     */
    public static Optional<TradeKey> from(
            VillagerTrades.ItemListing candidate,
            Entity entity,
            RandomSource random
    ) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(random, "random");
        MerchantOffer offer = candidate.getOffer(entity, random);
        return offer == null ? Optional.empty() : Optional.of(from(offer));
    }

    private static TradeKey.Item item(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        return new TradeKey.Item(
                itemId(stack.getItem()),
                stack.getCount(),
                components(stack.getComponentsPatch())
        );
    }

    private static String itemId(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id != null) {
            return id.toString();
        }
        return "unregistered:" + item.getClass().getName();
    }

    private static String components(DataComponentPatch patch) {
        if (patch.isEmpty()) {
            return "";
        }

        List<ComponentValue> values = new ArrayList<>();
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
            DataComponentType<?> type = entry.getKey();
            String typeId = componentId(type);
            String value = entry.getValue()
                    .map(component -> componentValue(type, component))
                    .orElse(REMOVED);
            values.add(new ComponentValue(typeId, value));
        }
        values.sort(Comparator.comparing(ComponentValue::type));

        StringBuilder result = new StringBuilder();
        for (ComponentValue value : values) {
            appendPart(result, value.type());
            appendPart(result, value.value());
        }
        return result.toString();
    }

    private static String componentId(DataComponentType<?> type) {
        ResourceLocation id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        if (id != null) {
            return id.toString();
        }
        return "unregistered:" + type.getClass().getName();
    }

    private static String componentValue(DataComponentType<?> type, Object value) {
        if ((type == DataComponents.ENCHANTMENTS || type == DataComponents.STORED_ENCHANTMENTS)
                && value instanceof ItemEnchantments enchantments) {
            return enchantments(enchantments);
        }

        try {
            DataResult<Tag> encoded = encodeComponent(type, value);
            Optional<Tag> tag = encoded.result();
            if (tag.isPresent()) {
                return canonicalTag(tag.orElseThrow());
            }
        } catch (RuntimeException ignored) {
            // A mod component may not support persistence outside its own context.
        }
        return "fallback:" + value.getClass().getName();
    }

    private static <T> DataResult<Tag> encodeComponent(DataComponentType<T> type, Object value) {
        TypedDataComponent<T> component = TypedDataComponent.createUnchecked(type, value);
        return component.encodeValue(NbtOps.INSTANCE);
    }

    private static String enchantments(ItemEnchantments enchantments) {
        List<String> values = new ArrayList<>();
        for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Holder<Enchantment>> entry
                : enchantments.entrySet()) {
            String id = entry.getKey().unwrapKey()
                    .map(key -> key.location().toString())
                    .orElseGet(() -> "unregistered:" + holderValueType(entry.getKey()));
            values.add(id + "@" + entry.getIntValue());
        }
        values.sort(String::compareTo);
        return String.join(",", values);
    }

    private static String holderValueType(Holder<Enchantment> holder) {
        Enchantment value = holder.value();
        return value == null ? "unknown" : value.getClass().getName();
    }

    private static String canonicalTag(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            List<String> keys = new ArrayList<>(compound.getAllKeys());
            keys.sort(String::compareTo);
            StringBuilder result = new StringBuilder("compound[");
            for (String key : keys) {
                appendPart(result, key);
                appendPart(result, canonicalTag(Objects.requireNonNull(compound.get(key))));
            }
            return result.append(']').toString();
        }
        if (tag instanceof ListTag list) {
            StringBuilder result = new StringBuilder("list[");
            for (Tag element : list) {
                appendPart(result, canonicalTag(element));
            }
            return result.append(']').toString();
        }
        return tag.getId() + ":" + tag;
    }

    private static void appendPart(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value);
    }

    private static TradeKey fallbackFor(MerchantOffer offer) {
        return new TradeKey.Fallback("merchant-offer:" + offer.getClass().getName());
    }

    private record ComponentValue(String type, String value) {
    }

    public record Identity(TradeKey key, boolean stable) {
        public Identity {
            Objects.requireNonNull(key, "key");
            if (stable && !isStable(key)) {
                throw new IllegalArgumentException("Only structured portable keys are stable");
            }
        }
    }
}
