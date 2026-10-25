package com.blueprintbuilder.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.blueprintbuilder.blueprint.BlueprintRegistry;
import com.blueprintbuilder.blueprint.LoadedBlueprint;
import com.blueprintbuilder.blockentity.ConstructionChestBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

public class BlueprintLibraryScreen extends Screen {
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 230;
    private static final int CARD_HEIGHT = 78;

    private final List<LoadedBlueprint> blueprints = new ArrayList<>();
    private double scroll;
    private boolean generateCamp;

    public BlueprintLibraryScreen() {
        super(Component.translatable("screen.blueprintbuilder.library"));
    }

    @Override
    protected void init() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            BlueprintRegistry.ensureLoaded(ClientResources.manager(minecraft), minecraft.level.registryAccess());
        }
        blueprints.clear();
        blueprints.addAll(BlueprintRegistry.all());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xF0181614);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 2, 0xFFC4A36A);
        graphics.fill(left, top + PANEL_HEIGHT - 2, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFFC4A36A);
        graphics.drawString(font, title, left + 12, top + 10, 0xFFF2E6C9, false);
        graphics.drawString(font, Component.translatable("screen.blueprintbuilder.library_hint"), left + 12, top + PANEL_HEIGHT - 28, 0xFFB5AA96, false);
        drawCheckbox(graphics, left + 12, top + PANEL_HEIGHT - 16, mouseX, mouseY);

        int listTop = top + 28;
        int listBottom = top + PANEL_HEIGHT - 36;
        int maxScroll = Math.max(0, blueprints.size() * CARD_HEIGHT - (listBottom - listTop));
        scroll = Mth.clamp(scroll, 0, maxScroll);
        graphics.enableScissor(left + 8, listTop, left + PANEL_WIDTH - 8, listBottom);
        if (blueprints.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.blueprintbuilder.empty"), left + 16, listTop + 8, 0xFFFFAAAA, false);
        }
        int index = 0;
        for (LoadedBlueprint blueprint : blueprints) {
            int cardTop = listTop + index * CARD_HEIGHT - (int) scroll;
            drawCard(graphics, blueprint, left + 12, cardTop, mouseX, mouseY);
            index++;
        }
        graphics.disableScissor();
    }

    private void drawCard(GuiGraphics graphics, LoadedBlueprint blueprint, int x, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + 316 && mouseY >= y && mouseY <= y + CARD_HEIGHT - 6;
        graphics.fill(x, y, x + 316, y + CARD_HEIGHT - 6, hovered ? 0xFF3A3328 : 0xFF2A241C);
        graphics.fill(x, y, x + 3, y + CARD_HEIGHT - 6, 0xFFC4A36A);
        drawThumbnail(graphics, blueprint, x + 8, y + 8);
        graphics.drawString(font, blueprint.name(), x + 78, y + 6, 0xFFF6E7C1, false);
        graphics.drawString(font, blueprint.category(), x + 78, y + 18, 0xFFD0B48A, false);
        graphics.drawString(font, Component.translatable("screen.blueprintbuilder.dimensions", blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ()), x + 78, y + 30, 0xFFCFCFCF, false);
        String description = trim(blueprint.description().getString(), 42);
        graphics.drawString(font, description, x + 78, y + 42, 0xFFB7B7B7, false);
        String progress = progressText(blueprint);
        if (progress != null) {
            graphics.drawString(font, Component.translatable("screen.blueprintbuilder.progress", progress), x + 78, y + 54, 0xFF8FCB7A, false);
        }
        int iconX = x + 230;
        int shown = 0;
        for (Map.Entry<String, Integer> material : blueprint.materials().entrySet()) {
            if (shown >= 4) {
                break;
            }
            ItemStack stack = stackFor(material.getKey());
            graphics.renderItem(stack, iconX, y + 8);
            graphics.drawString(font, Integer.toString(material.getValue()), iconX + 2, y + 26, 0xFFFFFFFF, true);
            iconX += 20;
            shown++;
        }
    }

    private void drawThumbnail(GuiGraphics graphics, LoadedBlueprint blueprint, int originX, int originY) {
        graphics.fill(originX, originY, originX + 64, originY + 56, 0xFF120F0C);
        if (minecraft.level == null) {
            return;
        }
        var registries = minecraft.level.registryAccess();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        record Plot(int x, int y, int color) {
        }
        List<Plot> plots = new ArrayList<>();
        for (var block : blueprint.blocks()) {
            if (com.blueprintbuilder.logic.MaterialCalculator.isIgnored(block.cell().blockId())) {
                continue;
            }
            var state = blueprint.stateFor(block.cell(), net.minecraft.world.level.block.Rotation.NONE, registries);
            int color = 0xFF000000 | (state.getMapColor(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).col & 0xFFFFFF);
            int px = (block.cell().x() - block.cell().z()) * 3;
            int py = (block.cell().x() + block.cell().z()) * 2 - block.cell().y() * 3;
            plots.add(new Plot(px, py, color));
            minX = Math.min(minX, px);
            minY = Math.min(minY, py);
            maxX = Math.max(maxX, px);
            maxY = Math.max(maxY, py);
        }
        if (plots.isEmpty()) {
            return;
        }
        float scale = Math.min(56F / Math.max(1, maxX - minX + 4), 48F / Math.max(1, maxY - minY + 4));
        for (Plot plot : plots) {
            int dx = originX + 4 + Math.round((plot.x - minX) * scale);
            int dy = originY + 48 - Math.round((plot.y - minY) * scale);
            int size = Math.max(2, Math.round(3 * scale));
            graphics.fill(dx, dy, dx + size, dy + size, plot.color);
        }
    }

    private void drawCheckbox(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        graphics.fill(x, y, x + 12, y + 12, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + 11, y + 11, generateCamp ? 0xFF6FBF58 : 0xFF4A453C);
        if (generateCamp) {
            graphics.drawString(font, "x", x + 3, y + 2, 0xFF10200C, false);
        }
        graphics.drawString(font, Component.translatable("screen.blueprintbuilder.generate_camp"), x + 16, y + 2, 0xFFF2E6C9, false);
    }

    private String progressText(LoadedBlueprint blueprint) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !(minecraft.level.getChunkSource() instanceof ClientChunkCache chunks)) {
            return null;
        }
        int best = -1;
        int total = 0;
        int centerX = minecraft.player.blockPosition().getX() >> 4;
        int centerZ = minecraft.player.blockPosition().getZ() >> 4;
        for (int chunkX = centerX - 4; chunkX <= centerX + 4; chunkX++) {
            for (int chunkZ = centerZ - 4; chunkZ <= centerZ + 4; chunkZ++) {
                LevelChunk chunk = chunks.getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof ConstructionChestBlockEntity chest && blueprint.id().equals(chest.blueprintId())) {
                        int placed = chest.placed().cardinality();
                        if (placed > best) {
                            best = placed;
                            total = blueprint.stepCount(com.blueprintbuilder.construction.PlacementValidator.rotation(chest.rotationOrdinal()));
                        }
                    }
                }
            }
        }
        return best < 0 ? null : best + "/" + total;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        int boxX = left + 12;
        int boxY = top + PANEL_HEIGHT - 16;
        if (mouseX >= boxX && mouseX <= boxX + 220 && mouseY >= boxY && mouseY <= boxY + 12) {
            generateCamp = !generateCamp;
            return true;
        }
        int listTop = top + 28;
        int listBottom = top + PANEL_HEIGHT - 36;
        if (mouseY < listTop || mouseY > listBottom) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int index = 0;
        for (LoadedBlueprint blueprint : blueprints) {
            int cardTop = listTop + index * CARD_HEIGHT - (int) scroll;
            int cardLeft = left + 12;
            if (mouseX >= cardLeft && mouseX <= cardLeft + 316 && mouseY >= cardTop && mouseY <= cardTop + CARD_HEIGHT - 6) {
                PlacementController.start(blueprint.id(), generateCamp);
                onClose();
                return true;
            }
            index++;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, scroll - scrollY * 16);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static ItemStack stackFor(String itemId) {
        ResourceLocation id = ResourceLocation.parse(itemId);
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            return new ItemStack(Items.BARRIER);
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }

    private static String trim(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 1) + "…";
    }
}
