package com.blueprintbuilder.construction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.blueprintbuilder.BlueprintBuilderMod;
import com.blueprintbuilder.blockentity.ConstructionChestBlockEntity;
import com.blueprintbuilder.blueprint.BlueprintRegistry;
import com.blueprintbuilder.blueprint.LoadedBlueprint;
import com.blueprintbuilder.camp.ConstructionCampGenerator;
import com.blueprintbuilder.logic.BuildStep;
import com.blueprintbuilder.logic.StructureCell;
import com.blueprintbuilder.network.PlaceBlueprintPayload;
import com.blueprintbuilder.registry.ModBlocks;
import com.blueprintbuilder.world.ReplaceableBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class PlacementValidator {
    private static final double MAX_DISTANCE_SQR = 16 * 16;

    private PlacementValidator() {
    }

    public static void place(ServerPlayer player, PlaceBlueprintPayload payload) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlueprintRegistry.ensureLoaded(level.getServer().getResourceManager(), level.registryAccess());
        LoadedBlueprint blueprint = BlueprintRegistry.get(payload.blueprintId());
        if (blueprint == null) {
            player.displayClientMessage(Component.translatable("message.blueprintbuilder.unknown_blueprint"), false);
            return;
        }
        Rotation rotation = rotation(payload.rotationOrdinal());
        BlockPos anchor = payload.anchor();
        if (player.distanceToSqr(Vec3.atCenterOf(anchor)) > MAX_DISTANCE_SQR) {
            player.displayClientMessage(Component.translatable("message.blueprintbuilder.too_far"), false);
            return;
        }
        if (!footprintClear(level, blueprint, anchor, rotation)) {
            player.displayClientMessage(Component.translatable("message.blueprintbuilder.placement_blocked"), false);
            return;
        }
        BlockPos chestPos = findChest(level, blueprint, anchor, rotation);
        if (chestPos == null) {
            player.displayClientMessage(Component.translatable("message.blueprintbuilder.no_chest_space"), false);
            return;
        }
        level.setBlock(chestPos, ModBlocks.CONSTRUCTION_CHEST.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(chestPos) instanceof ConstructionChestBlockEntity chest)) {
            BlueprintBuilderMod.LOGGER.error("Construction chest at {} did not create its block entity", chestPos);
            return;
        }
        chest.assign(blueprint.id(), anchor, rotation.ordinal());
        if (payload.generateCamp()) {
            boolean camp = ConstructionCampGenerator.tryGenerate(level, blueprint, anchor, rotation, chestPos);
            if (!camp) {
                player.displayClientMessage(Component.translatable("message.blueprintbuilder.camp_no_space"), false);
            }
        }
        player.displayClientMessage(Component.translatable("message.blueprintbuilder.placed", blueprint.name()), false);
        BlueprintBuilderMod.LOGGER.info("Player {} started {} at {}", player.getGameProfile().getName(), blueprint.id(), anchor);
    }

    public static boolean footprintClear(ServerLevel level, LoadedBlueprint blueprint, BlockPos anchor, Rotation rotation) {
        for (BuildStep step : blueprint.queue(rotation)) {
            if (!cellClear(level, blueprint, anchor, rotation, step.primary())) {
                return false;
            }
            if (step.secondary() != null && !cellClear(level, blueprint, anchor, rotation, step.secondary())) {
                return false;
            }
        }
        return true;
    }

    private static boolean cellClear(ServerLevel level, LoadedBlueprint blueprint, BlockPos anchor, Rotation rotation, StructureCell cell) {
        BlockPos pos = anchor.offset(cell.x(), cell.y(), cell.z());
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState current = level.getBlockState(pos);
        BlockState desired = blueprint.stateFor(cell, rotation, level.registryAccess());
        return current.equals(desired) || ReplaceableBlocks.isReplaceable(current);
    }

    public static @Nullable BlockPos findChest(ServerLevel level, LoadedBlueprint blueprint, BlockPos anchor, Rotation rotation) {
        Set<Long> footprint = new HashSet<>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        BlockPos preferred = null;
        for (BuildStep step : blueprint.queue(rotation)) {
            for (StructureCell cell : cells(step)) {
                BlockPos pos = anchor.offset(cell.x(), cell.y(), cell.z());
                footprint.add(pos.asLong());
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxZ = Math.max(maxZ, pos.getZ());
                if (preferred == null && step.itemId().endsWith("_door") && cell == step.primary()) {
                    Direction facing = Direction.byName(cell.property("facing"));
                    if (facing == null) {
                        facing = Direction.SOUTH;
                    }
                    preferred = pos.relative(rotation.rotate(facing).getOpposite());
                }
            }
        }
        List<BlockPos> candidates = new ArrayList<>();
        if (preferred != null) {
            candidates.add(preferred);
            candidates.add(preferred.relative(Direction.NORTH));
            candidates.add(preferred.relative(Direction.SOUTH));
            candidates.add(preferred.relative(Direction.EAST));
            candidates.add(preferred.relative(Direction.WEST));
        }
        for (int x = minX - 1; x <= maxX + 1; x++) {
            candidates.add(new BlockPos(x, minY, minZ - 1));
            candidates.add(new BlockPos(x, minY, maxZ + 1));
        }
        for (int z = minZ; z <= maxZ; z++) {
            candidates.add(new BlockPos(minX - 1, minY, z));
            candidates.add(new BlockPos(maxX + 1, minY, z));
        }
        for (BlockPos candidate : candidates) {
            if (footprint.contains(candidate.asLong()) || !level.isLoaded(candidate)) {
                continue;
            }
            if (!ReplaceableBlocks.isReplaceable(level.getBlockState(candidate))) {
                continue;
            }
            BlockPos below = candidate.below();
            if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    public static Set<Long> footprint(LoadedBlueprint blueprint, BlockPos anchor, Rotation rotation) {
        Set<Long> footprint = new HashSet<>();
        for (BuildStep step : blueprint.queue(rotation)) {
            for (StructureCell cell : cells(step)) {
                footprint.add(anchor.offset(cell.x(), cell.y(), cell.z()).asLong());
            }
        }
        return footprint;
    }

    private static List<StructureCell> cells(BuildStep step) {
        if (step.secondary() == null) {
            return List.of(step.primary());
        }
        return List.of(step.primary(), step.secondary());
    }

    public static Rotation rotation(int ordinal) {
        Rotation[] values = Rotation.values();
        return values[Math.floorMod(ordinal, values.length)];
    }
}
