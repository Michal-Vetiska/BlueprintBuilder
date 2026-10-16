package com.blueprintbuilder.registry;

import com.blueprintbuilder.BlueprintBuilderMod;
import com.blueprintbuilder.block.ConstructionChestBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BlueprintBuilderMod.MOD_ID);

    public static final DeferredBlock<ConstructionChestBlock> CONSTRUCTION_CHEST = BLOCKS.register("construction_chest",
            () -> new ConstructionChestBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .pushReaction(PushReaction.BLOCK)));

    private ModBlocks() {
    }
}
