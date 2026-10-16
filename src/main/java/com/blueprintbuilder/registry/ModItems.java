package com.blueprintbuilder.registry;

import com.blueprintbuilder.BlueprintBuilderMod;

import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BlueprintBuilderMod.MOD_ID);

    public static final DeferredItem<BlockItem> CONSTRUCTION_CHEST = ITEMS.registerSimpleBlockItem("construction_chest", ModBlocks.CONSTRUCTION_CHEST);

    private ModItems() {
    }
}
