package org.waterflane.villager_potential.core;

import java.util.regex.Pattern;

/**
 * Platform-independent identifier for a group of villager trades.
 */
public record TradeCategoryId(String namespace, String path) {
    private static final Pattern VALID_NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern VALID_PATH = Pattern.compile("[a-z0-9/._-]+");

    public TradeCategoryId {
        if (namespace == null || !VALID_NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid trade category namespace: " + namespace);
        }
        if (path == null || !VALID_PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Invalid trade category path: " + path);
        }
    }

    public static TradeCategoryId parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Trade category ID cannot be null");
        }

        int separator = value.indexOf(':');
        if (separator < 1 || separator != value.lastIndexOf(':') || separator == value.length() - 1) {
            throw new IllegalArgumentException("Invalid namespaced trade category ID: " + value);
        }

        return new TradeCategoryId(value.substring(0, separator), value.substring(separator + 1));
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
