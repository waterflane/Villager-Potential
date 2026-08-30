package org.waterflane.villager_potential;

import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.junit.jupiter.api.Test;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.SpecializationId;
import org.waterflane.villager_potential.core.TradeCategoryId;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpecializationDefinitionManagerTest {
    private static final ProfessionId LIBRARIAN = ProfessionId.parse("minecraft:librarian");

    @Test
    void validDefinitionLoadsFromServerDataResources() {
        SpecializationDefinitionManager manager = new SpecializationDefinitionManager();

        reload(manager, Map.of("test:librarian", definition(
                "minecraft:librarian",
                "villager_potential:librarian/enchanter",
                "villager_potential:enchanted_books",
                2.5
        )));

        var profession = manager.definitionFor(LIBRARIAN).orElseThrow();
        var enchanter = profession.specialization(
                SpecializationId.parse("villager_potential:librarian/enchanter")
        ).orElseThrow();
        assertEquals(
                2.5,
                enchanter.weightModifierFor(
                        TradeCategoryId.parse("villager_potential:enchanted_books")
                )
        );
        assertEquals(
                1.0,
                enchanter.weightModifierFor(TradeCategoryId.parse("test:unmentioned"))
        );
    }

    @Test
    void malformedDefinitionIsRejectedWithResourceAndFieldContext() {
        SpecializationDefinitionManager manager = new SpecializationDefinitionManager();
        String malformed = definition(
                "minecraft:librarian",
                "villager_potential:librarian/enchanter",
                "Not A Namespaced ID",
                2.5
        );

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> reload(manager, Map.of("test:broken", malformed))
        );

        JsonParseException cause = assertInstanceOf(JsonParseException.class, exception.getCause());
        assertTrue(cause.getMessage().contains("test:broken"));
        assertTrue(cause.getMessage().contains("trade category"));
        assertTrue(manager.definitions().isEmpty());
    }

    @Test
    void invalidJsonSyntaxRejectsTheReloadInsteadOfBeingSkipped() {
        SpecializationDefinitionManager manager = new SpecializationDefinitionManager();

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> reload(manager, Map.of("test:invalid_json", "{ not json"))
        );

        assertTrue(exception.getCause().getMessage().contains("test:invalid_json"));
        assertTrue(exception.getCause().getMessage().contains("invalid JSON"));
        assertTrue(manager.definitions().isEmpty());
    }

    @Test
    void invalidWeightIsRejected() {
        SpecializationDefinitionManager manager = new SpecializationDefinitionManager();

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> reload(manager, Map.of("test:negative_weight", definition(
                        "minecraft:librarian",
                        "villager_potential:librarian/enchanter",
                        "villager_potential:enchanted_books",
                        -0.25
                )))
        );

        assertTrue(exception.getCause().getMessage().contains("finite and non-negative"));
        assertTrue(manager.definitions().isEmpty());
    }

    @Test
    void malformedReloadRetainsThePreviousUsableDefinitions() {
        SpecializationDefinitionManager manager = new SpecializationDefinitionManager();
        reload(manager, Map.of("test:librarian", definition(
                "minecraft:librarian",
                "test:enchanter",
                "test:books",
                2.0
        )));
        var previous = manager.definitions();

        assertThrows(
                CompletionException.class,
                () -> reload(manager, Map.of("test:broken", "{ not json"))
        );

        assertSame(previous, manager.definitions());
        assertTrue(manager.definitionFor(LIBRARIAN).isPresent());
    }

    @Test
    void duplicateProfessionDefinitionsAreRejectedAtomically() {
        SpecializationDefinitionManager manager = new SpecializationDefinitionManager();
        Map<String, String> resources = new LinkedHashMap<>();
        resources.put("first:librarian", definition(
                "minecraft:librarian",
                "first:enchanter",
                "first:books",
                2.0
        ));
        resources.put("second:librarian", definition(
                "minecraft:librarian",
                "second:scribe",
                "second:paper",
                1.5
        ));

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> reload(manager, resources)
        );

        assertTrue(exception.getCause().getMessage().contains("duplicate definition"));
        assertTrue(exception.getCause().getMessage().contains("minecraft:librarian"));
        assertTrue(manager.definitions().isEmpty());
    }

    @Test
    void reloadReplacesPreviousDefinitions() {
        SpecializationDefinitionManager manager = new SpecializationDefinitionManager();
        reload(manager, Map.of("test:librarian", definition(
                "minecraft:librarian",
                "test:enchanter",
                "test:books",
                2.0
        )));

        reload(manager, Map.of("test:farmer", definition(
                "minecraft:farmer",
                "test:horticulturist",
                "test:crops",
                1.75
        )));

        assertFalse(manager.definitions().containsKey(LIBRARIAN));
        assertTrue(manager.definitions().containsKey(ProfessionId.parse("minecraft:farmer")));
        assertEquals(1, manager.definitions().size());
    }

    @Test
    void definitionMayUseOnlyItsGeneralSpecialization() {
        SpecializationDefinitionManager manager = new SpecializationDefinitionManager();

        reload(manager, Map.of("test:librarian", """
                {
                  "format_version": 1,
                  "profession": "minecraft:librarian",
                  "general_specialization": "test:librarian/general",
                  "specializations": []
                }
                """));

        var definition = manager.definitionFor(LIBRARIAN).orElseThrow();
        assertTrue(definition.namedSpecializations().isEmpty());
        assertEquals(
                SpecializationId.parse("test:librarian/general"),
                definition.defaultSpecialization()
        );
    }

    private static String definition(
            String profession,
            String specialization,
            String category,
            double weight
    ) {
        String general = specialization.substring(0, specialization.indexOf(':') + 1) + "general";
        return """
                {
                  "format_version": 1,
                  "profession": "%s",
                  "general_specialization": "%s",
                  "specializations": [
                    {
                      "id": "%s",
                      "trade_categories": {
                        "%s": %s
                      }
                    }
                  ]
                }
                """.formatted(profession, general, specialization, category, weight);
    }

    private static void reload(
            SpecializationDefinitionManager manager,
            Map<String, String> jsonById
    ) {
        ResourceManager resourceManager = mock(ResourceManager.class);
        PackResources pack = mock(PackResources.class);
        Map<ResourceLocation, Resource> resources = new LinkedHashMap<>();
        jsonById.forEach((id, json) -> {
            ResourceLocation definitionId = ResourceLocation.parse(id);
            ResourceLocation file = ResourceLocation.fromNamespaceAndPath(
                    definitionId.getNamespace(),
                    SpecializationDefinitionManager.DIRECTORY
                            + "/" + definitionId.getPath() + ".json"
            );
            resources.put(
                    file,
                    new Resource(
                            pack,
                            () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))
                    )
            );
        });
        when(resourceManager.listResources(eq(SpecializationDefinitionManager.DIRECTORY), any()))
                .thenReturn(resources);

        ProfilerFiller profiler = mock(ProfilerFiller.class);
        manager.reload(
                new ImmediateBarrier(),
                resourceManager,
                profiler,
                profiler,
                Runnable::run,
                Runnable::run
        ).join();
    }

    private static final class ImmediateBarrier implements PreparableReloadListener.PreparationBarrier {
        @Override
        public <T> CompletableFuture<T> wait(T backgroundResult) {
            return CompletableFuture.completedFuture(backgroundResult);
        }
    }
}
