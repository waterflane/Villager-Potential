package org.waterflane.villager_potential;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.ProfessionSpecializationDefinition;
import org.waterflane.villager_potential.core.SpecializationDefinition;
import org.waterflane.villager_potential.core.SpecializationId;
import org.waterflane.villager_potential.core.TradeCategoryId;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads the version-neutral specialization format from server data packs.
 */
public final class SpecializationDefinitionManager extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = Villager_potential.MODID + "/specializations";
    public static final int FORMAT_VERSION = 1;
    public static final SpecializationDefinitionManager INSTANCE =
            new SpecializationDefinitionManager();

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    private volatile Map<ProfessionId, ProfessionSpecializationDefinition> definitions = Map.of();

    SpecializationDefinitionManager() {
        super(GSON, DIRECTORY);
    }

    public Map<ProfessionId, ProfessionSpecializationDefinition> definitions() {
        return definitions;
    }

    public Optional<ProfessionSpecializationDefinition> definitionFor(ProfessionId professionId) {
        return Optional.ofNullable(definitions.get(professionId));
    }

    /**
     * Vanilla's JSON helper logs and skips syntax errors. Definitions are
     * configuration, so read them here and fail the reload instead.
     */
    @Override
    protected Map<ResourceLocation, JsonElement> prepare(
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        FileToIdConverter converter = FileToIdConverter.json(DIRECTORY);
        LinkedHashMap<ResourceLocation, JsonElement> resources = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry
                : converter.listMatchingResources(resourceManager).entrySet()) {
            ResourceLocation definitionId = converter.fileToId(entry.getKey());
            try (Reader reader = entry.getValue().openAsReader()) {
                resources.put(definitionId, JsonParser.parseReader(reader));
            } catch (IOException | JsonParseException exception) {
                throw malformed(definitionId, "invalid JSON", exception);
            }
        }
        return resources;
    }

    @Override
    protected void apply(
            Map<ResourceLocation, JsonElement> resources,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        LinkedHashMap<ProfessionId, ProfessionSpecializationDefinition> loaded = new LinkedHashMap<>();
        LinkedHashMap<ProfessionId, ResourceLocation> sources = new LinkedHashMap<>();

        resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .forEach(entry -> {
                    ProfessionSpecializationDefinition definition = parse(entry.getKey(), entry.getValue());
                    ResourceLocation previousSource = sources.putIfAbsent(
                            definition.professionId(),
                            entry.getKey()
                    );
                    if (previousSource != null) {
                        throw malformed(
                                entry.getKey(),
                                "duplicate definition for profession " + definition.professionId()
                                        + " (already defined by " + previousSource + ")"
                        );
                    }
                    loaded.put(definition.professionId(), definition);
                });

        definitions = Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
        LOGGER.info("Loaded {} villager specialization definitions", loaded.size());
    }

    private static ProfessionSpecializationDefinition parse(
            ResourceLocation source,
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
                                "weight for trade category '" + categoryEntry.getKey() + "' must be a number"
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

    private static JsonElement requireField(
            ResourceLocation source,
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
            ResourceLocation source,
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
            ResourceLocation source,
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
            ResourceLocation source,
            String location,
            JsonElement value
    ) {
        if (value == null || !value.isJsonObject()) {
            throw malformed(source, location + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static JsonParseException malformed(ResourceLocation source, String detail) {
        return new JsonParseException("Malformed specialization definition " + source + ": " + detail);
    }

    private static JsonParseException malformed(
            ResourceLocation source,
            String detail,
            Throwable cause
    ) {
        return new JsonParseException(
                "Malformed specialization definition " + source + ": " + detail,
                cause
        );
    }
}
