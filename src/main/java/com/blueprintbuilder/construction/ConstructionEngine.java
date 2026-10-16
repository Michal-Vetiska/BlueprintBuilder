package com.blueprintbuilder.construction;

import java.util.BitSet;
import java.util.OptionalInt;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.blueprintbuilder.BlueprintBuilderConfig;
import com.blueprintbuilder.BlueprintBuilderMod;
import com.blueprintbuilder.blockentity.ConstructionChestBlockEntity;
import com.blueprintbuilder.blueprint.BlueprintRegistry;
import com.blueprintbuilder.blueprint.LoadedBlueprint;
import com.blueprintbuilder.logic.BuildStep;
import com.blueprintbuilder.logic.ConstructionQueue;
import com.blueprintbuilder.logic.StructureCell;
import com.blueprintbuilder.world.ReplaceableBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class ConstructionEngine {
    private static final int PARTICLE_BLOCK_EVENT = 2001;

    private ConstructionEngine() {
    }

    public static int totalSteps(ConstructionChestBlockEntity chest) {
        LoadedBlueprint blueprint = blueprint(chest);
        if (blueprint == null) {
            return 0;
        }
        return blueprint.stepCount(rotation(chest));
    }

    public static void tick(Level level, ConstructionChestBlockEntity chest) {
        if (!(level instanceof ServerLevel server) || chest.blueprintId() == null) {
            return;
        }
        BlueprintRegistry.ensureLoaded(server.getServer().getResourceManager(), server.registryAccess());
        LoadedBlueprint blueprint = blueprint(chest);
        if (blueprint == null) {
            return;
        }
        Rotation rotation = rotation(chest);
        var queue = blueprint.queue(rotation);
        if (!chest.reconciled()) {
            reconcile(server, chest, blueprint, queue, rotation);
            chest.setReconciled(true);
        }
        if (chest.placed().cardinality() >= queue.size()) {
            announceComplete(server, chest);
            return;
        }

        double speed = BlueprintBuilderConfig.BLOCKS_PER_SECOND.get();
        int maxPerTick = BlueprintBuilderConfig.MAX_BLOCKS_PER_TICK.get();
        chest.setAccumulator(Math.min(maxPerTick, chest.accumulator() + speed / 20.0D));
        int budget = (int) chest.accumulator();
        if (budget <= 0) {
            countBlocked(server, chest, blueprint, queue, rotation);
            return;
        }

        Set<String> available = chest.availableItems();
        BitSet skip = new BitSet();
        int placedNow = 0;
        HolderLookup.Provider registries = server.registryAccess();
        while (placedNow < budget) {
            OptionalInt next = ConstructionQueue.nextBuildable(queue, chest.placed(), available, skip);
            if (next.isEmpty()) {
                break;
            }
            BuildStep step = queue.get(next.getAsInt());
            PlaceResult result = place(server, chest, blueprint, step, rotation, registries);
            if (result == PlaceResult.BLOCKED || result == PlaceResult.MISSING || result == PlaceResult.FAILED) {
                skip.set(step.index());
                continue;
            }
            chest.placed().set(step.index());
            chest.setAccumulator(chest.accumulator() - 1);
            placedNow++;
            if (result == PlaceResult.PLACED) {
                if (!chest.consumeOne(step.itemId())) {
                    BlueprintBuilderMod.LOGGER.error("Placed {} at {} but the matching item was no longer in the chest", step.itemId(), chest.getBlockPos());
                } else {
                    available = chest.availableItems();
                }
            }
        }
        chest.setTickCounter(chest.tickCounter() + 1);
        if (chest.tickCounter() % 20 == 0 || placedNow > 0) {
            countBlocked(server, chest, blueprint, queue, rotation);
            chest.sync();
        }
        if (chest.placed().cardinality() >= queue.size()) {
            announceComplete(server, chest);
        }
    }

    private static void reconcile(ServerLevel level, ConstructionChestBlockEntity chest, LoadedBlueprint blueprint, java.util.List<BuildStep> queue, Rotation rotation) {
        HolderLookup.Provider registries = level.registryAccess();
        boolean changed = false;
        for (BuildStep step : queue) {
            if (chest.placed().get(step.index())) {
                continue;
            }
            if (occupancy(level, chest, blueprint, step, rotation, registries) == Occupancy.PRESENT) {
                chest.placed().set(step.index());
                changed = true;
            }
        }
        if (changed) {
            chest.sync();
        }
    }

    private static PlaceResult place(ServerLevel level, ConstructionChestBlockEntity chest, LoadedBlueprint blueprint, BuildStep step, Rotation rotation, HolderLookup.Provider registries) {
        Occupancy occupancy = occupancy(level, chest, blueprint, step, rotation, registries);
        if (occupancy == Occupancy.BLOCKED) {
            return PlaceResult.BLOCKED;
        }
        if (occupancy == Occupancy.PRESENT) {
            return PlaceResult.ALREADY;
        }
        if (!chest.availableItems().contains(step.itemId())) {
            return PlaceResult.MISSING;
        }
        boolean placedAny = putCell(level, chest, blueprint, step.primary(), rotation, registries, false);
        if (step.secondary() != null) {
            placedAny |= putCell(level, chest, blueprint, step.secondary(), rotation, registries, false);
        }
        finishCell(level, chest, blueprint, step.primary(), rotation, registries);
        if (step.secondary() != null) {
            finishCell(level, chest, blueprint, step.secondary(), rotation, registries);
        }
        if (occupancy(level, chest, blueprint, step, rotation, registries) != Occupancy.PRESENT) {
            return PlaceResult.FAILED;
        }
        return placedAny ? PlaceResult.PLACED : PlaceResult.ALREADY;
    }

    private static boolean putCell(ServerLevel level, ConstructionChestBlockEntity chest, LoadedBlueprint blueprint, StructureCell cell, Rotation rotation, HolderLookup.Provider registries, boolean unused) {
        BlockPos pos = chest.anchor().offset(cell.x(), cell.y(), cell.z());
        BlockState desired = blueprint.stateFor(cell, rotation, registries);
        if (level.getBlockState(pos).equals(desired)) {
            return false;
        }
        level.setBlock(pos, desired, Block.UPDATE_CLIENTS);
        return level.getBlockState(pos).equals(desired);
    }

    private static void finishCell(ServerLevel level, ConstructionChestBlockEntity chest, LoadedBlueprint blueprint, StructureCell cell, Rotation rotation, HolderLookup.Provider registries) {
        BlockPos pos = chest.anchor().offset(cell.x(), cell.y(), cell.z());
        BlockState desired = blueprint.stateFor(cell, rotation, registries);
        if (!level.getBlockState(pos).equals(desired)) {
            return;
        }
        CompoundTag extra = blueprint.blockEntityTag(rotation, cell.x(), cell.y(), cell.z());
        if (extra != null) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                CompoundTag copy = extra.copy();
                copy.putInt("x", pos.getX());
                copy.putInt("y", pos.getY());
                copy.putInt("z", pos.getZ());
                blockEntity.loadWithComponents(copy, registries);
            }
        }
        level.updateNeighborsAt(pos, desired.getBlock());
        level.levelEvent(PARTICLE_BLOCK_EVENT, pos, Block.getId(desired));
        SoundType sound = desired.getSoundType(level, pos, null);
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, 0.8F, 0.9F + level.getRandom().nextFloat() * 0.15F);
    }

    private static Occupancy occupancy(ServerLevel level, ConstructionChestBlockEntity chest, LoadedBlueprint blueprint, BuildStep step, Rotation rotation, HolderLookup.Provider registries) {
        Occupancy primary = cellOccupancy(level, chest, blueprint, step.primary(), rotation, registries);
        if (primary == Occupancy.BLOCKED) {
            return Occupancy.BLOCKED;
        }
        if (step.secondary() == null) {
            return primary;
        }
        Occupancy secondary = cellOccupancy(level, chest, blueprint, step.secondary(), rotation, registries);
        if (secondary == Occupancy.BLOCKED) {
            return Occupancy.BLOCKED;
        }
        if (primary == Occupancy.PRESENT && secondary == Occupancy.PRESENT) {
            return Occupancy.PRESENT;
        }
        return Occupancy.REPLACEABLE;
    }

    private static Occupancy cellOccupancy(ServerLevel level, ConstructionChestBlockEntity chest, LoadedBlueprint blueprint, StructureCell cell, Rotation rotation, HolderLookup.Provider registries) {
        BlockPos pos = chest.anchor().offset(cell.x(), cell.y(), cell.z());
        if (!level.isLoaded(pos)) {
            return Occupancy.BLOCKED;
        }
        BlockState current = level.getBlockState(pos);
        BlockState desired = blueprint.stateFor(cell, rotation, registries);
        if (current.equals(desired)) {
            return Occupancy.PRESENT;
        }
        if (ReplaceableBlocks.isReplaceable(current)) {
            return Occupancy.REPLACEABLE;
        }
        return Occupancy.BLOCKED;
    }

    private static void countBlocked(ServerLevel level, ConstructionChestBlockEntity chest, LoadedBlueprint blueprint, java.util.List<BuildStep> queue, Rotation rotation) {
        HolderLookup.Provider registries = level.registryAccess();
        int blocked = 0;
        for (BuildStep step : queue) {
            if (chest.placed().get(step.index())) {
                continue;
            }
            if (occupancy(level, chest, blueprint, step, rotation, registries) == Occupancy.BLOCKED) {
                blocked++;
            }
        }
        chest.setBlockedCount(blocked);
    }

    private static void announceComplete(ServerLevel level, ConstructionChestBlockEntity chest) {
        if (chest.announcedComplete()) {
            return;
        }
        chest.setAnnouncedComplete(true);
        chest.sync();
        Component message = Component.translatable("message.blueprintbuilder.complete");
        Vec3 origin = Vec3.atCenterOf(chest.getBlockPos());
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(origin) < 64 * 64) {
                player.displayClientMessage(message, false);
            }
        }
        BlueprintBuilderMod.LOGGER.info("Construction finished at {}", chest.getBlockPos());
    }

    private static @Nullable LoadedBlueprint blueprint(ConstructionChestBlockEntity chest) {
        if (chest.blueprintId() == null) {
            return null;
        }
        return BlueprintRegistry.get(chest.blueprintId());
    }

    private static Rotation rotation(ConstructionChestBlockEntity chest) {
        Rotation[] values = Rotation.values();
        int ordinal = Math.floorMod(chest.rotationOrdinal(), values.length);
        return values[ordinal];
    }

    private enum Occupancy {
        PRESENT,
        REPLACEABLE,
        BLOCKED
    }

    private enum PlaceResult {
        PLACED,
        ALREADY,
        BLOCKED,
        MISSING,
        FAILED
    }
}
