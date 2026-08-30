package org.waterflane.villager_potential.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Canonical, versioned metadata used by portable item trade keys. */
public final class TradeMetadata {
    public static final String FORMAT_PREFIX = "vp1:";
    private static final Set<String> PORTABLE_VANILLA_KEYS = Set.of(
            "minecraft:custom_name",
            "minecraft:dyed_color",
            "minecraft:enchantments",
            "minecraft:potion_contents",
            "minecraft:stored_enchantments",
            "minecraft:suspicious_stew_effects",
            "minecraft:trim"
    );
    private static final Set<String> IGNORED_DYNAMIC_KEYS = Set.of(
            "minecraft:map_id",
            "minecraft:map_decorations",
            "minecraft:map_color"
    );

    private TradeMetadata() {
    }

    public static String canonical(Map<String, String> entries) {
        Objects.requireNonNull(entries, "entries");
        if (entries.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder(FORMAT_PREFIX);
        new TreeMap<>(entries).forEach((key, value) -> {
            appendPart(result, requirePart("metadata key", key));
            appendPart(result, requirePart("metadata value", value));
        });
        return result.toString();
    }

    /** Normalizes native NBT/component fields into the shared vanilla subset. */
    public static String canonicalVanilla(Map<String, String> nativeEntries) {
        Objects.requireNonNull(nativeEntries, "nativeEntries");
        Map<String, String> portable = new LinkedHashMap<>();
        nativeEntries.forEach((key, value) -> {
            if (IGNORED_DYNAMIC_KEYS.contains(key)) {
                return;
            }
            portable.put(
                    key,
                    PORTABLE_VANILLA_KEYS.contains(key)
                            ? value
                            : "fallback:native-metadata:" + key
            );
        });
        return canonical(portable);
    }

    public static Map<String, String> parse(String metadata) {
        Objects.requireNonNull(metadata, "metadata");
        if (metadata.isEmpty()) {
            return Map.of();
        }
        if (!metadata.startsWith(FORMAT_PREFIX)) {
            throw new IllegalArgumentException("Unsupported trade metadata format");
        }
        List<String> parts = parseParts(metadata, FORMAT_PREFIX.length());
        if ((parts.size() & 1) != 0) {
            throw new IllegalArgumentException("Trade metadata must contain key/value pairs");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < parts.size(); index += 2) {
            if (result.put(parts.get(index), parts.get(index + 1)) != null) {
                throw new IllegalArgumentException("Duplicate trade metadata key: " + parts.get(index));
            }
        }
        return Map.copyOf(result);
    }

    /** Converts the unversioned component stream written by schema 9/10. */
    public static String migrateLegacyComponents(String legacy) {
        Objects.requireNonNull(legacy, "legacy");
        if (legacy.isEmpty() || legacy.startsWith(FORMAT_PREFIX)) {
            return legacy;
        }
        List<String> parts;
        try {
            parts = parseParts(legacy, 0);
        } catch (IllegalArgumentException exception) {
            return canonical(Map.of("legacy:unreadable", "fallback:legacy-components"));
        }
        if ((parts.size() & 1) != 0) {
            return canonical(Map.of("legacy:unreadable", "fallback:legacy-components"));
        }
        Map<String, String> migrated = new LinkedHashMap<>();
        for (int index = 0; index < parts.size(); index += 2) {
            String key = parts.get(index);
            String value = parts.get(index + 1);
            if (IGNORED_DYNAMIC_KEYS.contains(key)) {
                continue;
            }
            if (PORTABLE_VANILLA_KEYS.contains(key)) {
                migrated.put(key, value);
            } else {
                migrated.put(key, "fallback:legacy-component:" + key);
            }
        }
        return canonical(migrated);
    }

    public static boolean isPortable(String metadata) {
        if (metadata.isEmpty()) {
            return true;
        }
        if (!metadata.startsWith(FORMAT_PREFIX)) {
            return false;
        }
        try {
            return parse(metadata).entrySet().stream().noneMatch(entry ->
                    entry.getKey().startsWith("unregistered:")
                            || entry.getValue().contains("unregistered:")
                            || entry.getValue().contains("fallback:")
            );
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static List<String> parseParts(String value, int start) {
        List<String> parts = new ArrayList<>();
        int cursor = start;
        while (cursor < value.length()) {
            int separator = value.indexOf(':', cursor);
            if (separator < 0 || separator == cursor) {
                throw new IllegalArgumentException("Malformed length-prefixed metadata");
            }
            int length;
            try {
                length = Integer.parseInt(value.substring(cursor, separator));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Malformed metadata length", exception);
            }
            if (length < 0 || separator + 1 + length > value.length()) {
                throw new IllegalArgumentException("Metadata part exceeds input");
            }
            int contentStart = separator + 1;
            parts.add(value.substring(contentStart, contentStart + length));
            cursor = contentStart + length;
        }
        return parts;
    }

    private static String requirePart(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }

    private static void appendPart(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value);
    }
}
