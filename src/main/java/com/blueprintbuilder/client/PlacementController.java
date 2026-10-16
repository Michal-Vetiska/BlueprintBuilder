package com.blueprintbuilder.client;

import org.jetbrains.annotations.Nullable;

import com.blueprintbuilder.blueprint.BlueprintRegistry;
import com.blueprintbuilder.network.PlaceBlueprintPayload;
import com.blueprintbuilder.world.ReplaceableBlocks;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PlacementController {
    private static boolean active;
    private static @Nullable ResourceLocation blueprintId;
    private static int rotation;
    private static boolean generateCamp;
    private static BlockPos anchor = BlockPos.ZERO;

    private PlacementController() {
    }

    public static void start(ResourceLocation blueprintId, boolean generateCamp) {
        active = true;
        PlacementController.blueprintId = blueprintId;
        PlacementController.generateCamp = generateCamp;
        rotation = 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            updateAnchor(minecraft.player);
        }
    }

    private static int closePauseTicks;

    public static void cancel() {
        active = false;
        blueprintId = null;
    }

    public static void requestClosePause() {
        closePauseTicks = 5;
        cancel();
    }

    public static boolean shouldClosePause() {
        if (closePauseTicks <= 0) {
            return false;
        }
        closePauseTicks--;
        return true;
    }

    public static boolean isActive() {
        return active && blueprintId != null;
    }

    public static @Nullable ResourceLocation blueprintId() {
        return blueprintId;
    }

    public static Rotation rotation() {
        return Rotation.values()[Math.floorMod(rotation, Rotation.values().length)];
    }

    public static int rotationOrdinal() {
        return Math.floorMod(rotation, Rotation.values().length);
    }

    public static boolean generateCamp() {
        return generateCamp;
    }

    public static BlockPos anchor() {
        return anchor;
    }

    public static void rotate(int direction) {
        rotation = Math.floorMod(rotation + (direction >= 0 ? 1 : -1), Rotation.values().length);
    }

    public static void updateAnchor(Player player) {
        HitResult hit = player.pick(8.0D, 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || blockHit.getType() == HitResult.Type.MISS) {
            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        if (ReplaceableBlocks.isReplaceable(player.level().getBlockState(pos))) {
            anchor = pos.immutable();
        } else {
            anchor = pos.relative(blockHit.getDirection()).immutable();
        }
    }

    public static void confirm() {
        if (!isActive() || blueprintId == null || BlueprintRegistry.get(blueprintId) == null) {
            cancel();
            return;
        }
        PacketDistributor.sendToServer(new PlaceBlueprintPayload(blueprintId, anchor, rotationOrdinal(), generateCamp));
        cancel();
    }
}
