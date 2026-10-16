package com.blueprintbuilder;

import org.slf4j.Logger;

import com.blueprintbuilder.network.ModNetworking;
import com.blueprintbuilder.registry.ModBlockEntities;
import com.blueprintbuilder.registry.ModBlocks;
import com.blueprintbuilder.registry.ModCreativeTabs;
import com.blueprintbuilder.registry.ModItems;
import com.blueprintbuilder.registry.ModMenus;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(BlueprintBuilderMod.MOD_ID)
public class BlueprintBuilderMod {
    public static final String MOD_ID = "blueprintbuilder";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BlueprintBuilderMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        modEventBus.addListener(ModNetworking::register);
        modContainer.registerConfig(ModConfig.Type.SERVER, BlueprintBuilderConfig.SPEC);
        LOGGER.info("Blueprint Builder loading");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Blueprint Builder common setup complete");
    }
}
