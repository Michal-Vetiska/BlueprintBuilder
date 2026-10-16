package com.blueprintbuilder.registry;

import com.blueprintbuilder.BlueprintBuilderMod;
import com.blueprintbuilder.blockentity.ConstructionChestBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BlueprintBuilderMod.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConstructionChestBlockEntity>> CONSTRUCTION_CHEST = BLOCK_ENTITIES.register(
            "construction_chest",
            () -> BlockEntityType.Builder.of(ConstructionChestBlockEntity::new, ModBlocks.CONSTRUCTION_CHEST.get()).build(null));

    private ModBlockEntities() {
    }
}
