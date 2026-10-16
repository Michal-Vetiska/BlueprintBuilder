package com.blueprintbuilder.registry;

import com.blueprintbuilder.BlueprintBuilderMod;
import com.blueprintbuilder.menu.ConstructionChestMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, BlueprintBuilderMod.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<ConstructionChestMenu>> CONSTRUCTION_CHEST = MENUS.register(
            "construction_chest",
            () -> IMenuTypeExtension.create(ConstructionChestMenu::new));

    private ModMenus() {
    }
}
