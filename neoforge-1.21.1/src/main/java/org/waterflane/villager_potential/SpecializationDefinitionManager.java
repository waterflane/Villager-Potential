package org.waterflane.villager_potential;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
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
import org.waterflane.villager_potential.core.SpecializationDefinitionParser;

import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Loads the version-neutral specialization format from server data packs.
 */
public final class SpecializationDefinitionManager extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = Villager_potential.MODID + "/specializations";
    public static final int FORMAT_VERSION = SpecializationDefinitionParser.FORMAT_VERSION;
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
        // The format contract is loader-neutral and lives in core.
        return SpecializationDefinitionParser.parse(source.toString(), json);
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
