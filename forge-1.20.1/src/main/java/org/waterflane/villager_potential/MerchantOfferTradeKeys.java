package org.waterflane.villager_potential;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.waterflane.villager_potential.core.TradeKey;
import org.waterflane.villager_potential.core.TradeMetadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Builds portable logical identities from the legacy item NBT used by 1.20.1. */
public final class MerchantOfferTradeKeys {
    private MerchantOfferTradeKeys() {
    }

    public static TradeKey from(MerchantOffer offer) {
        return identify(offer).key();
    }

    public static Identity identify(MerchantOffer offer) {
        Objects.requireNonNull(offer, "offer");
        try {
            TradeKey.Item costA = item(offer.getBaseCostA());
            ItemStack costBStack = offer.getCostB();
            Optional<TradeKey.Item> costB = costBStack.isEmpty()
                    ? Optional.empty()
                    : Optional.of(item(costBStack));
            ItemStack baseResult = offer instanceof DemandPriceOffer demandPriceOffer
                    ? demandPriceOffer.villagerPotential$baseResult()
                    : offer.getResult();
            TradeKey key = new TradeKey.Offer(costA, costB, item(baseResult));
            return new Identity(key, isStable(key));
        } catch (RuntimeException exception) {
            return new Identity(fallbackFor(offer), false);
        }
    }

    public static boolean isStable(TradeKey key) {
        return TradeKey.isStable(key);
    }

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
                metadata(stack.getTag())
        );
    }

    private static String itemId(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id == null ? "unregistered:" + item.getClass().getName() : id.toString();
    }

    private static String metadata(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return "";
        }

        Map<String, String> values = new LinkedHashMap<>();
        List<String> unknownKeys = new ArrayList<>();
        for (String key : tag.getAllKeys()) {
            Tag value = tag.get(key);
            if (value == null) {
                continue;
            }
            switch (key) {
                case "Enchantments" -> values.put(
                        "minecraft:enchantments",
                        enchantments(value)
                );
                case "StoredEnchantments" -> values.put(
                        "minecraft:stored_enchantments",
                        enchantments(value)
                );
                case "Potion", "CustomPotionEffects", "CustomPotionColor" -> values.put(
                        "minecraft:potion_contents",
                        potionContents(tag)
                );
                case "Effects" -> values.put(
                        "minecraft:suspicious_stew_effects",
                        stewEffects(value)
                );
                case "Trim" -> values.put("minecraft:trim", trim(value));
                case "display" -> readDisplay(values, value);
                case "map", "Decorations" -> {
                    // The generated map id and marker coordinates are not trade identity.
                }
                case "Damage", "RepairCost" -> {
                    if (tag.getInt(key) != 0) {
                        unknownKeys.add(key);
                    }
                }
                default -> unknownKeys.add(key);
            }
        }
        unknownKeys.sort(String::compareTo);
        for (String key : unknownKeys) {
            values.put("legacy_nbt:" + key, canonicalTag(Objects.requireNonNull(tag.get(key))));
        }
        return TradeMetadata.canonicalVanilla(values);
    }

    private static void readDisplay(Map<String, String> values, Tag value) {
        if (!(value instanceof CompoundTag display)) {
            values.put("legacy_nbt:display", canonicalTag(value));
            return;
        }
        if (display.contains("Name")) {
            Component name = Component.Serializer.fromJson(display.getString("Name"));
            if (name == null) {
                values.put("legacy_nbt:display.Name", "fallback:invalid-component");
            } else {
                values.put("minecraft:custom_name", name.getString());
            }
        }
        if (display.contains("color")) {
            values.put("minecraft:dyed_color", Integer.toString(display.getInt("color")));
        }
        for (String key : display.getAllKeys()) {
            if (!key.equals("Name") && !key.equals("color")) {
                values.put("legacy_nbt:display." + key,
                        canonicalTag(Objects.requireNonNull(display.get(key))));
            }
        }
    }

    private static String potionContents(CompoundTag tag) {
        CompoundTag normalized = new CompoundTag();
        if (tag.contains("Potion", Tag.TAG_STRING)) {
            normalized.putString("potion", tag.getString("Potion"));
        }
        if (tag.contains("CustomPotionColor", Tag.TAG_ANY_NUMERIC)) {
            normalized.putInt("custom_color", tag.getInt("CustomPotionColor"));
        }
        if (tag.contains("CustomPotionEffects", Tag.TAG_LIST)) {
            ListTag effects = new ListTag();
            for (Tag effect : tag.getList("CustomPotionEffects", Tag.TAG_COMPOUND)) {
                effects.add(normalizeEffect((CompoundTag) effect));
            }
            if (!effects.isEmpty()) {
                normalized.put("custom_effects", effects);
            }
        }
        return canonicalTag(normalized);
    }

    private static String stewEffects(Tag tag) {
        if (!(tag instanceof ListTag list)) {
            return canonicalTag(tag);
        }
        ListTag normalized = new ListTag();
        for (Tag entry : list) {
            if (!(entry instanceof CompoundTag effect)) {
                return "fallback:invalid-stew-effects";
            }
            CompoundTag value = new CompoundTag();
            value.putString("id", effectId(effect, "EffectId"));
            value.putInt("duration", effect.getInt("EffectDuration"));
            normalized.add(value);
        }
        return canonicalTag(normalized);
    }

    private static String trim(Tag tag) {
        if (!(tag instanceof CompoundTag trim)
                || !trim.contains("material", Tag.TAG_STRING)
                || !trim.contains("pattern", Tag.TAG_STRING)) {
            return "fallback:invalid-trim";
        }
        CompoundTag normalized = new CompoundTag();
        normalized.putString("material", trim.getString("material"));
        normalized.putString("pattern", trim.getString("pattern"));
        return canonicalTag(normalized);
    }

    private static CompoundTag normalizeEffect(CompoundTag effect) {
        CompoundTag normalized = new CompoundTag();
        normalized.putString("id", effectId(effect, "Id"));
        normalized.putInt("amplifier", effect.getByte("Amplifier"));
        normalized.putInt("duration", effect.getInt("Duration"));
        normalized.putBoolean("ambient", effect.getBoolean("Ambient"));
        normalized.putBoolean(
                "show_particles",
                !effect.contains("ShowParticles") || effect.getBoolean("ShowParticles")
        );
        normalized.putBoolean(
                "show_icon",
                !effect.contains("ShowIcon") || effect.getBoolean("ShowIcon")
        );
        return normalized;
    }

    private static String effectId(CompoundTag effect, String key) {
        if (effect.contains(key, Tag.TAG_STRING)) {
            return effect.getString(key);
        }
        int numericId = effect.getByte(key) & 255;
        ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(
                BuiltInRegistries.MOB_EFFECT.byId(numericId)
        );
        return id == null ? "unregistered:mob_effect:" + numericId : id.toString();
    }

    private static String enchantments(Tag tag) {
        if (!(tag instanceof ListTag list)) {
            return canonicalTag(tag);
        }
        List<String> values = new ArrayList<>();
        for (Tag entry : list) {
            if (entry instanceof CompoundTag enchantment) {
                values.add(enchantment.getString("id") + "@" + enchantment.getShort("lvl"));
            } else {
                values.add(canonicalTag(entry));
            }
        }
        values.sort(String::compareTo);
        return String.join(",", values);
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

    public record Identity(TradeKey key, boolean stable) {
        public Identity {
            Objects.requireNonNull(key, "key");
            if (stable && !isStable(key)) {
                throw new IllegalArgumentException("Only structured portable keys are stable");
            }
        }
    }
}
