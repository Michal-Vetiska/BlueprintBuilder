package com.blueprintbuilder.blueprint;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.blueprintbuilder.logic.BuildStep;
import com.blueprintbuilder.logic.ConstructionQueue;
import com.blueprintbuilder.logic.MaterialCalculator;
import com.blueprintbuilder.logic.StructureCell;
import com.blueprintbuilder.world.BlueprintGeometry;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

public final class LoadedBlueprint {
    private final ResourceLocation id;
    private final String category;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final List<TemplateBlock> blocks;
    private final Map<String, Integer> materials;
    private final Map<Rotation, List<BuildStep>> queues;
    private final Map<Rotation, Map<String, CompoundTag>> blockEntityTags;
    private final Map<String, BlockState> stateCache = new ConcurrentHashMap<>();

    public LoadedBlueprint(ResourceLocation id, String category, List<TemplateBlock> blocks) {
        this.id = id;
        this.category = category;
        int maxX = 0;
        int maxY = 0;
        int maxZ = 0;
        List<StructureCell> cells = new ArrayList<>(blocks.size());
        for (TemplateBlock block : blocks) {
            cells.add(block.cell());
            maxX = Math.max(maxX, block.cell().x());
            maxY = Math.max(maxY, block.cell().y());
            maxZ = Math.max(maxZ, block.cell().z());
        }
        this.blocks = List.copyOf(blocks);
        this.sizeX = maxX + 1;
        this.sizeY = maxY + 1;
        this.sizeZ = maxZ + 1;
        this.materials = MaterialCalculator.calculate(cells);
        this.queues = new EnumMap<>(Rotation.class);
        this.blockEntityTags = new EnumMap<>(Rotation.class);
        for (Rotation rotation : Rotation.values()) {
            List<StructureCell> rotatedCells = new ArrayList<>(blocks.size());
            Map<String, CompoundTag> tags = new HashMap<>();
            for (TemplateBlock block : blocks) {
                var rotated = BlueprintGeometry.rotate(block.cell().x(), block.cell().y(), block.cell().z(), rotation, sizeX, sizeZ);
                StructureCell cell = new StructureCell(rotated.getX(), rotated.getY(), rotated.getZ(), block.cell().blockId(), block.cell().properties());
                rotatedCells.add(cell);
                if (block.blockEntityTag() != null) {
                    tags.put(ConstructionQueue.key(cell.x(), cell.y(), cell.z()), block.blockEntityTag());
                }
            }
            queues.put(rotation, ConstructionQueue.build(rotatedCells));
            blockEntityTags.put(rotation, Map.copyOf(tags));
        }
    }

    public ResourceLocation id() {
        return id;
    }

    public String categoryId() {
        return category;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public int sizeX(Rotation rotation) {
        return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90 ? sizeZ : sizeX;
    }

    public int sizeZ(Rotation rotation) {
        return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90 ? sizeX : sizeZ;
    }

    public Map<String, Integer> materials() {
        return materials;
    }

    public List<TemplateBlock> blocks() {
        return blocks;
    }

    public List<BuildStep> queue(Rotation rotation) {
        return queues.get(rotation);
    }

    public int stepCount(Rotation rotation) {
        return queue(rotation).size();
    }

    public @Nullable CompoundTag blockEntityTag(Rotation rotation, int x, int y, int z) {
        return blockEntityTags.get(rotation).get(ConstructionQueue.key(x, y, z));
    }

    public Component name() {
        return Component.translatable("blueprint." + id.getNamespace() + "." + id.getPath() + ".name");
    }

    public Component description() {
        return Component.translatable("blueprint." + id.getNamespace() + "." + id.getPath() + ".description");
    }

    public Component category() {
        return Component.translatable("category.blueprintbuilder." + category.toLowerCase(Locale.ROOT));
    }

    public BlockState stateFor(StructureCell cell, Rotation rotation, HolderLookup.Provider registries) {
        String cacheKey = cell.blockId() + cell.properties() + "#" + rotation.ordinal();
        return stateCache.computeIfAbsent(cacheKey, ignored -> parse(cell, registries).rotate(rotation));
    }

    private static BlockState parse(StructureCell cell, HolderLookup.Provider registries) {
        StringBuilder text = new StringBuilder(cell.blockId());
        if (!cell.properties().isEmpty()) {
            text.append('[');
            boolean first = true;
            for (var entry : cell.properties().entrySet()) {
                if (!first) {
                    text.append(',');
                }
                first = false;
                text.append(entry.getKey()).append('=').append(entry.getValue());
            }
            text.append(']');
        }
        try {
            return BlockStateParser.parseForBlock(registries.lookupOrThrow(Registries.BLOCK), text.toString(), false).blockState();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse block state " + text, exception);
        }
    }
}
