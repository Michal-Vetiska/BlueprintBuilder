package com.blueprintbuilder.client;

import org.lwjgl.glfw.GLFW;

import com.blueprintbuilder.BlueprintBuilderMod;
import com.blueprintbuilder.registry.ModMenus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

public final class ClientEvents {
    private ClientEvents() {
    }

    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void registerScreens(RegisterMenuScreensEvent event) {
            event.register(ModMenus.CONSTRUCTION_CHEST.get(), ConstructionChestScreen::new);
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(ModKeyMappings.OPEN_LIBRARY);
        }
    }

    @EventBusSubscriber(modid = BlueprintBuilderMod.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        private GameBus() {
        }

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) {
                return;
            }
            if (PlacementController.isActive()) {
                PlacementController.updateAnchor(minecraft.player);
            }
            if (PlacementController.shouldClosePause() && minecraft.screen instanceof PauseScreen) {
                minecraft.setScreen(null);
            }
            while (ModKeyMappings.OPEN_LIBRARY.consumeClick()) {
                if (minecraft.screen == null && minecraft.level != null) {
                    PlacementController.cancel();
                    minecraft.setScreen(new BlueprintLibraryScreen());
                }
            }
        }

        @SubscribeEvent
        public static void onMouse(InputEvent.MouseButton.Pre event) {
            if (!PlacementController.isActive() || Minecraft.getInstance().screen != null) {
                return;
            }
            if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && event.getAction() == GLFW.GLFW_PRESS) {
                PlacementController.confirm();
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onScroll(InputEvent.MouseScrollingEvent event) {
            if (!PlacementController.isActive() || Minecraft.getInstance().screen != null) {
                return;
            }
            PlacementController.rotate(event.getScrollDeltaY() > 0 ? 1 : -1);
            event.setCanceled(true);
        }

        @SubscribeEvent
        public static void onKey(InputEvent.Key event) {
            if (!PlacementController.isActive()) {
                return;
            }
            if (event.getKey() == GLFW.GLFW_KEY_ESCAPE && event.getAction() == GLFW.GLFW_PRESS) {
                PlacementController.requestClosePause();
            }
        }

        @SubscribeEvent
        public static void render(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                return;
            }
            PlacementPreviewRenderer.render(event);
        }
    }
}
