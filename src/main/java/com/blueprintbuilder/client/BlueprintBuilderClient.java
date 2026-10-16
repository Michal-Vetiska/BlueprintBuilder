package com.blueprintbuilder.client;

import com.blueprintbuilder.BlueprintBuilderMod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = BlueprintBuilderMod.MOD_ID, dist = Dist.CLIENT)
public class BlueprintBuilderClient {
    public BlueprintBuilderClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(ClientEvents.ModBus::registerScreens);
        modEventBus.addListener(ClientEvents.ModBus::registerKeys);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
