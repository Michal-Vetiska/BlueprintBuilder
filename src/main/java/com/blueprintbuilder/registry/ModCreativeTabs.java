package com.blueprintbuilder.registry;

import com.blueprintbuilder.BlueprintBuilderMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BlueprintBuilderMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.blueprintbuilder"))
            .icon(() -> new ItemStack(ModItems.CONSTRUCTION_CHEST.get()))
            .displayItems((parameters, output) -> output.accept(ModItems.CONSTRUCTION_CHEST.get()))
            .build());

    private ModCreativeTabs() {
    }
}
