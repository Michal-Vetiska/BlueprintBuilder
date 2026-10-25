package com.blueprintbuilder.client;

import java.util.Map;

import com.blueprintbuilder.blueprint.BlueprintRegistry;
import com.blueprintbuilder.blueprint.LoadedBlueprint;
import com.blueprintbuilder.menu.ConstructionChestMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ConstructionChestScreen extends AbstractContainerScreen<ConstructionChestMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/shulker_box.png");

    public ConstructionChestScreen(ConstructionChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 168;
        this.inventoryLabelY = 74;
    }

    @Override
    protected void init() {
        super.init();
        if (minecraft != null && minecraft.level != null) {
            BlueprintRegistry.ensureLoaded(ClientResources.manager(minecraft), minecraft.level.registryAccess());
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = (width - imageWidth) / 2;
        int top = (height - imageHeight) / 2;
        graphics.blit(TEXTURE, left, top, 0, 0, imageWidth, imageHeight, 256, 256);
        int panelLeft = left + imageWidth + 6;
        graphics.fill(panelLeft, top, panelLeft + 128, top + imageHeight, 0xF0181614);
        graphics.fill(panelLeft, top, panelLeft + 128, top + 2, 0xFFC4A36A);
        graphics.drawString(font, Component.translatable("screen.blueprintbuilder.materials"), panelLeft + 6, top + 6, 0xFFF2E6C9, false);
        LoadedBlueprint blueprint = blueprint();
        int y = top + 22;
        if (blueprint == null) {
            graphics.drawString(font, Component.translatable("screen.blueprintbuilder.no_blueprint"), panelLeft + 6, y, 0xFFFFAAAA, false);
            return;
        }
        int shown = 0;
        for (Map.Entry<String, Integer> material : blueprint.materials().entrySet()) {
            if (shown >= 7) {
                graphics.drawString(font, Component.translatable("screen.blueprintbuilder.more_materials", blueprint.materials().size() - shown), panelLeft + 6, y, 0xFFB7B7B7, false);
                break;
            }
            ItemStack stack = stackFor(material.getKey());
            int remaining = Math.max(0, material.getValue() - consumed(material.getKey()));
            graphics.renderItem(stack, panelLeft + 6, y);
            graphics.drawString(font, Component.literal(remaining + "/" + material.getValue()), panelLeft + 28, y + 4, remaining == 0 ? 0xFF8FCB7A : 0xFFF2E6C9, false);
            y += 18;
            shown++;
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0x404040, false);
        int total = Math.max(1, menu.total());
        int placed = Math.min(menu.placed(), total);
        int barWidth = 160;
        graphics.fill(8, imageHeight - 14, 8 + barWidth, imageHeight - 8, 0xFF2A241C);
        graphics.fill(8, imageHeight - 14, 8 + (barWidth * placed / total), imageHeight - 8, 0xFF6FBF58);
        String progress = placed + "/" + menu.total();
        if (menu.blocked() > 0) {
            progress = progress + "  " + Component.translatable("screen.blueprintbuilder.blocked", menu.blocked()).getString();
        }
        graphics.drawString(font, progress, 8, imageHeight - 24, 0xFF404040, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private LoadedBlueprint blueprint() {
        if (menu.chest() == null || menu.chest().blueprintId() == null || minecraft == null || minecraft.level == null) {
            return null;
        }
        BlueprintRegistry.ensureLoaded(ClientResources.manager(minecraft), minecraft.level.registryAccess());
        return BlueprintRegistry.get(menu.chest().blueprintId());
    }

    private int consumed(String itemId) {
        return menu.chest() == null ? 0 : menu.chest().consumed(itemId);
    }

    private static ItemStack stackFor(String itemId) {
        ResourceLocation id = ResourceLocation.parse(itemId);
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            return new ItemStack(Items.BARRIER);
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }
}
