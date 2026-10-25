package com.blueprintbuilder.blueprint;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.blueprintbuilder.BlueprintBuilderMod;
import com.blueprintbuilder.logic.StructureCell;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Reads blueprint metadata and structure NBT. A missing NBT file falls back to the companion block list,
 * which is compiled into the same structure template format before it is loaded.
 */
public final class BlueprintLoader {
    private BlueprintLoader() {
    }

    public static @Nullable LoadedBlueprint loadDefinition(ResourceLocation file, Resource resource, ResourceManager resources, HolderLookup.Provider registries) {
        try (InputStream input = resource.open()) {
            return loadDefinition(file, input, resources, registries);
        } catch (IOException exception) {
            BlueprintBuilderMod.LOGGER.error("Failed to read blueprint definition {}", file, exception);
            return null;
        }
    }

    public static @Nullable LoadedBlueprint loadDefinition(ResourceLocation file, InputStream input, ResourceManager resources, HolderLookup.Provider registries) {
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            String rawId = json.get("id").getAsString();
            ResourceLocation id = rawId.contains(":")
                    ? ResourceLocation.parse(rawId)
                    : ResourceLocation.fromNamespaceAndPath(BlueprintBuilderMod.MOD_ID, rawId);
            String category = json.has("category") ? json.get("category").getAsString() : "General";
            ResourceLocation structureId = json.has("structure")
                    ? ResourceLocation.parse(json.get("structure").getAsString())
                    : id;
            List<TemplateBlock> blocks = readStructure(resources, registries, structureId);
            if (blocks.isEmpty()) {
                BlueprintBuilderMod.LOGGER.error("Blueprint {} has no blocks and was skipped", id);
                return null;
            }
            return new LoadedBlueprint(id, category, blocks);
        } catch (RuntimeException | IOException exception) {
            BlueprintBuilderMod.LOGGER.error("Failed to read blueprint definition {}", file, exception);
            return null;
        }
    }

    public static @Nullable LoadedBlueprint loadStructure(ResourceManager resources, HolderLookup.Provider registries, ResourceLocation structureId, String category) {
        try {
            List<TemplateBlock> blocks = readStructure(resources, registries, structureId);
            if (blocks.isEmpty()) {
                return null;
            }
            return new LoadedBlueprint(structureId, category, blocks);
        } catch (RuntimeException exception) {
            BlueprintBuilderMod.LOGGER.error("Failed to read structure {}", structureId, exception);
            return null;
        }
    }

    private static List<TemplateBlock> readStructure(ResourceManager resources, HolderLookup.Provider registries, ResourceLocation structureId) {
        ResourceLocation nbtFile = ResourceLocation.fromNamespaceAndPath(structureId.getNamespace(), "structure/" + structureId.getPath() + ".nbt");
        CompoundTag tag = readNbt(resources, nbtFile);
        if (tag == null || !acceptTemplate(registries, structureId, tag)) {
            CompoundTag synthesized = synthesize(resources, structureId, registries);
            if (synthesized != null && acceptTemplate(registries, structureId, synthesized)) {
                tag = synthesized;
            }
        }
        if (tag == null || !acceptTemplate(registries, structureId, tag)) {
            BlueprintBuilderMod.LOGGER.error("Missing structure {} and no block-list fallback was found", structureId);
            return List.of();
        }
        return blocksFromTag(tag);
    }

    private static boolean acceptTemplate(HolderLookup.Provider registries, ResourceLocation structureId, CompoundTag tag) {
        try {
            StructureTemplate template = new StructureTemplate();
            template.load(registries.lookupOrThrow(Registries.BLOCK), tag);
            return template.getSize().getX() > 0 && template.getSize().getY() > 0 && template.getSize().getZ() > 0;
        } catch (RuntimeException exception) {
            BlueprintBuilderMod.LOGGER.error("Minecraft rejected structure template {}", structureId, exception);
            return false;
        }
    }

    private static @Nullable CompoundTag readNbt(ResourceManager resources, ResourceLocation file) {
        try (InputStream input = open(resources, file)) {
            if (input == null) {
                return null;
            }
            return NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
        } catch (IOException exception) {
            BlueprintBuilderMod.LOGGER.error("Could not read structure NBT {}", file, exception);
            return null;
        }
    }

    /**
     * The client resource manager only sees assets. Blueprint files live in the data pack,
     * so a missing pack entry falls back to the copy bundled inside the mod jar.
     */
    static @Nullable InputStream open(ResourceManager resources, ResourceLocation file) throws IOException {
        var optional = resources.getResource(file);
        if (optional.isPresent()) {
            return optional.get().open();
        }
        return BlueprintLoader.class.getResourceAsStream("/data/" + file.getNamespace() + "/" + file.getPath());
    }

    private static @Nullable CompoundTag synthesize(ResourceManager resources, ResourceLocation structureId, HolderLookup.Provider registries) {
        ResourceLocation jsonFile = ResourceLocation.fromNamespaceAndPath(structureId.getNamespace(), "blueprint/" + structureId.getPath() + ".blocks.json");
        try (InputStream input = open(resources, jsonFile)) {
            if (input == null) {
                return null;
            }
            return readBlockList(jsonFile, input);
        } catch (IOException | RuntimeException exception) {
            BlueprintBuilderMod.LOGGER.error("Could not read block list {}", jsonFile, exception);
            return null;
        }
    }

    private static @Nullable CompoundTag readBlockList(ResourceLocation jsonFile, InputStream input) {
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
            List<TemplateBlock> blocks = new ArrayList<>();
            int maxX = 0;
            int maxY = 0;
            int maxZ = 0;
            for (JsonElement element : array) {
                JsonObject object = element.getAsJsonObject();
                int x = object.get("x").getAsInt();
                int y = object.get("y").getAsInt();
                int z = object.get("z").getAsInt();
                Map<String, String> properties = new HashMap<>();
                if (object.has("properties")) {
                    JsonObject props = object.getAsJsonObject("properties");
                    for (String key : props.keySet()) {
                        properties.put(key, props.get(key).getAsString());
                    }
                }
                blocks.add(new TemplateBlock(new StructureCell(x, y, z, object.get("block").getAsString(), properties), null));
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);
            }
            BlueprintBuilderMod.LOGGER.warn("Structure NBT was missing. Built a template from {}", jsonFile);
            return toStructureTag(blocks, maxX + 1, maxY + 1, maxZ + 1);
        } catch (IOException | RuntimeException exception) {
            BlueprintBuilderMod.LOGGER.error("Could not read block list {}", jsonFile, exception);
            return null;
        }
    }

    static CompoundTag toStructureTag(List<TemplateBlock> blocks, int sizeX, int sizeY, int sizeZ) {
        List<String> paletteKeys = new ArrayList<>();
        Map<String, Integer> paletteIndex = new HashMap<>();
        ListTag palette = new ListTag();
        ListTag blockList = new ListTag();
        for (TemplateBlock block : blocks) {
            String key = block.cell().blockId() + block.cell().properties();
            int index = paletteIndex.computeIfAbsent(key, ignored -> {
                CompoundTag entry = new CompoundTag();
                entry.putString("Name", block.cell().blockId());
                if (!block.cell().properties().isEmpty()) {
                    CompoundTag properties = new CompoundTag();
                    block.cell().properties().entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .forEach(property -> properties.putString(property.getKey(), property.getValue()));
                    entry.put("Properties", properties);
                }
                palette.add(entry);
                paletteKeys.add(key);
                return paletteKeys.size() - 1;
            });
            CompoundTag entry = new CompoundTag();
            ListTag pos = new ListTag();
            pos.add(IntTag.valueOf(block.cell().x()));
            pos.add(IntTag.valueOf(block.cell().y()));
            pos.add(IntTag.valueOf(block.cell().z()));
            entry.put("pos", pos);
            entry.putInt("state", index);
            blockList.add(entry);
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", currentDataVersion());
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(sizeX));
        size.add(IntTag.valueOf(sizeY));
        size.add(IntTag.valueOf(sizeZ));
        tag.put("size", size);
        tag.put("entities", new ListTag());
        tag.put("blocks", blockList);
        tag.put("palette", palette);
        return tag;
    }

    private static int currentDataVersion() {
        return SharedConstants.WORLD_VERSION;
    }

    private static List<TemplateBlock> blocksFromTag(CompoundTag tag) {
        ListTag palette = paletteOf(tag);
        List<BlockStateInfo> states = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag entry = palette.getCompound(i);
            Map<String, String> properties = new HashMap<>();
            if (entry.contains("Properties", Tag.TAG_COMPOUND)) {
                CompoundTag props = entry.getCompound("Properties");
                for (String key : props.getAllKeys()) {
                    properties.put(key, props.getString(key));
                }
            }
            states.add(new BlockStateInfo(entry.getString("Name"), properties));
        }
        ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        List<TemplateBlock> result = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag entry = blocks.getCompound(i);
            ListTag pos = entry.getList("pos", Tag.TAG_INT);
            int stateIndex = entry.getInt("state");
            if (stateIndex < 0 || stateIndex >= states.size()) {
                continue;
            }
            BlockStateInfo info = states.get(stateIndex);
            CompoundTag nbt = entry.contains("nbt", Tag.TAG_COMPOUND) ? sanitize(entry.getCompound("nbt")) : null;
            result.add(new TemplateBlock(new StructureCell(pos.getInt(0), pos.getInt(1), pos.getInt(2), info.name(), info.properties()), nbt));
        }
        result.sort(Comparator.comparingInt((TemplateBlock block) -> block.cell().y())
                .thenComparingInt(block -> block.cell().z())
                .thenComparingInt(block -> block.cell().x()));
        return result;
    }

    private static ListTag paletteOf(CompoundTag tag) {
        if (tag.contains("palette", Tag.TAG_LIST)) {
            return tag.getList("palette", Tag.TAG_COMPOUND);
        }
        if (tag.contains("palettes", Tag.TAG_LIST)) {
            ListTag palettes = tag.getList("palettes", Tag.TAG_LIST);
            if (!palettes.isEmpty()) {
                return palettes.getList(0);
            }
        }
        return new ListTag();
    }

    private static @Nullable CompoundTag sanitize(@Nullable CompoundTag source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        CompoundTag copy = source.copy();
        for (String key : List.of("Items", "item", "Item", "LootTable", "LootTableSeed", "Book", "RecordItem", "id", "x", "y", "z")) {
            copy.remove(key);
        }
        return copy.isEmpty() ? null : copy;
    }

    private record BlockStateInfo(String name, Map<String, String> properties) {
        private BlockStateInfo {
            properties = Map.copyOf(properties);
        }
    }
}
