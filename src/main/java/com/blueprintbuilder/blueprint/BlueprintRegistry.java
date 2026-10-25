package com.blueprintbuilder.blueprint;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.blueprintbuilder.BlueprintBuilderMod;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public final class BlueprintRegistry {
    private static final String[] BUNDLED = {
            "medieval_cottage",
            "stone_keep",
            "timber_manor",
            "watchtower",
            "hill_windmill"
    };

    private static boolean loaded;
    private static List<LoadedBlueprint> blueprints = List.of();
    private static Map<ResourceLocation, LoadedBlueprint> byId = Map.of();
    private static @Nullable LoadedBlueprint camp;

    private BlueprintRegistry() {
    }

    public static synchronized void ensureLoaded(ResourceManager resources, HolderLookup.Provider registries) {
        if (loaded) {
            return;
        }
        Map<ResourceLocation, LoadedBlueprint> index = new LinkedHashMap<>();
        resources.listResources("blueprint", location -> location.getPath().endsWith(".json") && !location.getPath().endsWith(".blocks.json"))
                .forEach((location, resource) -> remember(index, BlueprintLoader.loadDefinition(location, resource, resources, registries)));
        if (index.isEmpty()) {
            // The screen runs on the client, whose resource manager does not contain data packs.
            for (String id : BUNDLED) {
                ResourceLocation file = ResourceLocation.fromNamespaceAndPath(BlueprintBuilderMod.MOD_ID, "blueprint/" + id + ".json");
                try (InputStream input = BlueprintLoader.open(resources, file)) {
                    if (input != null) {
                        remember(index, BlueprintLoader.loadDefinition(file, input, resources, registries));
                    }
                } catch (IOException exception) {
                    BlueprintBuilderMod.LOGGER.error("Failed to read bundled blueprint {}", id, exception);
                }
            }
        }
        List<LoadedBlueprint> found = new ArrayList<>(index.values());
        found.sort((left, right) -> left.id().compareTo(right.id()));
        index.clear();
        for (LoadedBlueprint blueprint : found) {
            index.put(blueprint.id(), blueprint);
        }
        camp = BlueprintLoader.loadStructure(resources, registries, ResourceLocation.fromNamespaceAndPath(BlueprintBuilderMod.MOD_ID, "construction_camp"), "camp");
        if (found.isEmpty()) {
            BlueprintBuilderMod.LOGGER.error("No blueprints could be read. The library will try again the next time it opens.");
            return;
        }
        if (camp == null) {
            BlueprintBuilderMod.LOGGER.warn("Construction camp structure is missing. The camp option will be unavailable.");
        }
        blueprints = List.copyOf(found);
        byId = Map.copyOf(index);
        loaded = true;
    }

    private static void remember(Map<ResourceLocation, LoadedBlueprint> index, @Nullable LoadedBlueprint blueprint) {
        if (blueprint == null || index.containsKey(blueprint.id())) {
            return;
        }
        index.put(blueprint.id(), blueprint);
        BlueprintBuilderMod.LOGGER.info("Loaded blueprint {} ({}x{}x{}, {} materials)",
                blueprint.id(), blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ(), blueprint.materials().size());
    }

    public static List<LoadedBlueprint> all() {
        return blueprints;
    }

    public static @Nullable LoadedBlueprint get(ResourceLocation id) {
        return byId.get(id);
    }

    public static @Nullable LoadedBlueprint camp() {
        return camp;
    }

    public static boolean isLoaded() {
        return loaded;
    }
}
