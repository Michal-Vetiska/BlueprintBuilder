package com.blueprintbuilder.camp;

import com.blueprintbuilder.BlueprintBuilderMod;
import com.blueprintbuilder.blueprint.BlueprintRegistry;
import com.blueprintbuilder.blueprint.LoadedBlueprint;
import com.blueprintbuilder.construction.PlacementValidator;
import com.blueprintbuilder.logic.BuildStep;
import com.blueprintbuilder.logic.StructureCell;
import com.blueprintbuilder.world.ReplaceableBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

public final class ConstructionCampGenerator {
    private ConstructionCampGenerator() {
    }

    public static boolean tryGenerate(ServerLevel level, LoadedBlueprint building, BlockPos anchor, Rotation rotation, BlockPos chestPos) {
        LoadedBlueprint camp = BlueprintRegistry.camp();
        if (camp == null) {
            BlueprintBuilderMod.LOGGER.warn("Skipped construction camp because its structure failed to load");
            return false;
        }
        var forbidden = PlacementValidator.footprint(building, anchor, rotation);
        forbidden.add(chestPos.asLong());
        BlockPos origin = buildingOrigin(building, anchor, rotation);
        for (int radius = 2; radius <= 10; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    BlockPos candidate = origin.offset(dx, 0, dz);
                    if (fits(level, camp, candidate, forbidden)) {
                        place(level, camp, candidate);
                        BlueprintBuilderMod.LOGGER.info("Placed construction camp at {}", candidate);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static BlockPos buildingOrigin(LoadedBlueprint building, BlockPos anchor, Rotation rotation) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (BuildStep step : building.queue(rotation)) {
            minX = Math.min(minX, anchor.getX() + step.primary().x());
            minY = Math.min(minY, anchor.getY() + step.primary().y());
            minZ = Math.min(minZ, anchor.getZ() + step.primary().z());
        }
        return new BlockPos(minX, minY, minZ);
    }

    private static boolean fits(ServerLevel level, LoadedBlueprint camp, BlockPos origin, java.util.Set<Long> forbidden) {
        for (BuildStep step : camp.queue(Rotation.NONE)) {
            if (!cellFits(level, camp, origin, step.primary(), forbidden)) {
                return false;
            }
            if (step.secondary() != null && !cellFits(level, camp, origin, step.secondary(), forbidden)) {
                return false;
            }
        }
        return true;
    }

    private static boolean cellFits(ServerLevel level, LoadedBlueprint camp, BlockPos origin, StructureCell cell, java.util.Set<Long> forbidden) {
        BlockPos pos = origin.offset(cell.x(), cell.y(), cell.z());
        if (!level.isLoaded(pos) || forbidden.contains(pos.asLong())) {
            return false;
        }
        BlockState current = level.getBlockState(pos);
        if (!ReplaceableBlocks.isReplaceable(current)) {
            return false;
        }
        if (cell.y() == 0) {
            BlockPos below = pos.below();
            if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                return false;
            }
        }
        return true;
    }

    private static void place(ServerLevel level, LoadedBlueprint camp, BlockPos origin) {
        var registries = level.registryAccess();
        for (BuildStep step : camp.queue(Rotation.NONE)) {
            put(level, camp, origin, step.primary(), registries);
            if (step.secondary() != null) {
                put(level, camp, origin, step.secondary(), registries);
            }
        }
        for (BuildStep step : camp.queue(Rotation.NONE)) {
            finish(level, camp, origin, step.primary(), registries);
            if (step.secondary() != null) {
                finish(level, camp, origin, step.secondary(), registries);
            }
        }
    }

    private static void put(ServerLevel level, LoadedBlueprint camp, BlockPos origin, StructureCell cell, net.minecraft.core.HolderLookup.Provider registries) {
        BlockPos pos = origin.offset(cell.x(), cell.y(), cell.z());
        BlockState state = camp.stateFor(cell, Rotation.NONE, registries);
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    private static void finish(ServerLevel level, LoadedBlueprint camp, BlockPos origin, StructureCell cell, net.minecraft.core.HolderLookup.Provider registries) {
        BlockPos pos = origin.offset(cell.x(), cell.y(), cell.z());
        BlockState state = camp.stateFor(cell, Rotation.NONE, registries);
        level.updateNeighborsAt(pos, state.getBlock());
    }
}
