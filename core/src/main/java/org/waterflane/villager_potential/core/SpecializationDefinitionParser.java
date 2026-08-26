package org.waterflane.villager_potential.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the version-neutral specialization definition data format.
 *
 * <p>The format contract lives here so every loader validates identical files
 * identically: {@code format_version} must match {@link #FORMAT_VERSION},
 * all ids are explicit namespaced ids, and category weights are finite and
 * non-negative. Resource discovery, reload ordering, and duplicate handling
 * remain platform integration concerns.</p>
 */
public final class SpecializationDefinitionParser {
    public static final int FORMAT_VERSION = 1;

    private SpecializationDefinitionParser() {
    }

    /**
     * Parses one definition read from a datapack resource. The source name is
     * an opaque label used verbatim in error messages.
     *
     * @throws JsonParseException when the document violates the format
     */
    public static ProfessionSpecializationDefinition parse(
            String source,
            JsonElement json
    ) {
        try {
            JsonObject root = requireObject(source, "root", json);
            double rawFormatVersion = requireNumber(source, root, "format_version").getAsDouble();
            if (!Double.isFinite(rawFormatVersion)
                    || rawFormatVersion != Math.rint(rawFormatVersion)
                    || rawFormatVersion != FORMAT_VERSION) {
                throw malformed(
                        source,
                        "unsupported format_version " + rawFormatVersion
                                + "; expected " + FORMAT_VERSION
                );
            }

            ProfessionId profession = ProfessionId.parse(
                    requireString(source, root, "profession")
            );
            SpecializationId general = SpecializationId.parse(
                    requireString(source, root, "general_specialization")
            );
            JsonElement specializationsElement = requireField(source, root, "specializations");
            if (!specializationsElement.isJsonArray()) {
                throw malformed(source, "field 'specializations' must be an array");
            }

            List<SpecializationDefinition> specializations = new ArrayList<>();
            for (int index = 0; index < specializationsElement.getAsJsonArray().size(); index++) {
                JsonObject specialization = requireObject(
                        source,
                        "specializations[" + index + "]",
                        specializationsElement.getAsJsonArray().get(index)
                );
                SpecializationId specializationId = SpecializationId.parse(
                        requireString(source, specialization, "id")
                );
                JsonObject categories = requireObject(
                        source,
                        "specializations[" + index + "].trade_categories",
                        requireField(source, specialization, "trade_categories")
                );
                if (categories.size() == 0) {
                    throw malformed(
                            source,
                            "specializations[" + index + "].trade_categories must not be empty"
                    );
                }

                LinkedHashMap<TradeCategoryId, Double> weights = new LinkedHashMap<>();
                categories.entrySet().forEach(categoryEntry -> {
                    TradeCategoryId categoryId = TradeCategoryId.parse(categoryEntry.getKey());
                    JsonElement weightElement = categoryEntry.getValue();
                    if (!(weightElement instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
                        throw malformed(
                                source,
                                "weight for trade category '" + categoryEntry.getKey()
                                        + "' must be a number"
                        );
                    }
                    double weight = primitive.getAsDouble();
                    if (!Double.isFinite(weight) || weight < 0.0) {
                        throw malformed(
                                source,
                                "weight for trade category '" + categoryEntry.getKey()
                                        + "' must be finite and non-negative: " + weight
                        );
                    }
                    weights.put(categoryId, weight);
                });
                specializations.add(new SpecializationDefinition(specializationId, weights));
            }
            return new ProfessionSpecializationDefinition(
                    profession,
                    new SpecializationDefinition(general, Map.of()),
                    specializations
            );
        } catch (JsonParseException exception) {
            throw exception;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw malformed(source, exception.getMessage(), exception);
        }
    }

    /**
     * Convenience for platforms or tools that only have raw JSON text. Syntax
     * errors are reported like resource-loading failures ("invalid JSON"),
     * matching how platform reload listeners report unreadable files.
     */
    public static ProfessionSpecializationDefinition parseText(String source, String json) {
        JsonElement element;
        try {
            element = JsonParser.parseString(json);
        } catch (RuntimeException exception) {
            throw malformed(source, "invalid JSON", exception);
        }
        return parse(source, element);
    }

    private static JsonElement requireField(
            String source,
            JsonObject object,
            String field
    ) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            throw malformed(source, "missing required field '" + field + "'");
        }
        return value;
    }

    private static String requireString(
            String source,
            JsonObject object,
            String field
    ) {
        JsonElement value = requireField(source, object, field);
        if (!(value instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw malformed(source, "field '" + field + "' must be a string");
        }
        return primitive.getAsString();
    }

    private static JsonPrimitive requireNumber(
            String source,
            JsonObject object,
            String field
    ) {
        JsonElement value = requireField(source, object, field);
        if (!(value instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            throw malformed(source, "field '" + field + "' must be a number");
        }
        return primitive;
    }

    private static JsonObject requireObject(
            String source,
            String location,
            JsonElement value
    ) {
        if (value == null || !value.isJsonObject()) {
            throw malformed(source, location + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static JsonParseException malformed(String source, String detail) {
        return new JsonParseException("Malformed specialization definition " + source + ": " + detail);
    }

    private static JsonParseException malformed(
            String source,
            String detail,
            Throwable cause
    ) {
        return new JsonParseException(
                "Malformed specialization definition " + source + ": " + detail,
                cause
        );
    }
}
