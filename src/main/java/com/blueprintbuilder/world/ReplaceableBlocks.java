package com.blueprintbuilder.world;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blocks the construction site is allowed to replace. Solids, fluids, and player builds are not in this set.
 */
public final class ReplaceableBlocks {
    private ReplaceableBlocks() {
    }

    public static boolean isReplaceable(BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.VINE)
                || state.is(Blocks.GLOW_LICHEN)
                || state.is(Blocks.MOSS_CARPET)
                || state.is(Blocks.CRIMSON_ROOTS)
                || state.is(Blocks.WARPED_ROOTS)
                || state.is(Blocks.NETHER_SPROUTS);
    }
}
