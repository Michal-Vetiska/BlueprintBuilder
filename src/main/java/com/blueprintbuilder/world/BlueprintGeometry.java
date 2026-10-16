package com.blueprintbuilder.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;

public final class BlueprintGeometry {
    private BlueprintGeometry() {
    }

    public static BlockPos rotate(int x, int y, int z, Rotation rotation, int sizeX, int sizeZ) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new BlockPos(sizeZ - 1 - z, y, x);
            case CLOCKWISE_180 -> new BlockPos(sizeX - 1 - x, y, sizeZ - 1 - z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(z, y, sizeX - 1 - x);
            default -> new BlockPos(x, y, z);
        };
    }
}
