package com.blueprintbuilder.blueprint;

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
        List<LoadedBlueprint> found = new ArrayList<>();
        resources.listResources("blueprint", location -> location.getPath().endsWith(".json") && !location.getPath().endsWith(".blocks.json"))
                .forEach((location, resource) -> {
                    LoadedBlueprint blueprint = BlueprintLoader.loadDefinition(location, resource, resources, registries);
                    if (blueprint != null) {
                        found.add(blueprint);
                        BlueprintBuilderMod.LOGGER.info("Loaded blueprint {} ({}x{}x{}, {} materials)",
                                blueprint.id(), blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ(), blueprint.materials().size());
                    }
                });
        found.sort((left, right) -> left.id().compareTo(right.id()));
        Map<ResourceLocation, LoadedBlueprint> index = new LinkedHashMap<>();
        for (LoadedBlueprint blueprint : found) {
            index.put(blueprint.id(), blueprint);
        }
        blueprints = List.copyOf(found);
        byId = Map.copyOf(index);
        camp = BlueprintLoader.loadStructure(resources, registries, ResourceLocation.fromNamespaceAndPath(BlueprintBuilderMod.MOD_ID, "construction_camp"), "camp");
        if (camp == null) {
            BlueprintBuilderMod.LOGGER.warn("Construction camp structure is missing. The camp option will be unavailable.");
        }
        loaded = true;
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
