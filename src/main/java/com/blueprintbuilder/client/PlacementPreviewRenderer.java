package com.blueprintbuilder.client;

import com.blueprintbuilder.blueprint.BlueprintRegistry;
import com.blueprintbuilder.blueprint.LoadedBlueprint;
import com.blueprintbuilder.logic.BuildStep;
import com.blueprintbuilder.logic.StructureCell;
import com.blueprintbuilder.world.ReplaceableBlocks;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class PlacementPreviewRenderer {
    private PlacementPreviewRenderer() {
    }

    public static void render(RenderLevelStageEvent event) {
        if (!PlacementController.isActive()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || PlacementController.blueprintId() == null) {
            return;
        }
        BlueprintRegistry.ensureLoaded(ClientResources.manager(minecraft), minecraft.level.registryAccess());
        LoadedBlueprint blueprint = BlueprintRegistry.get(PlacementController.blueprintId());
        if (blueprint == null) {
            return;
        }
        Rotation rotation = PlacementController.rotation();
        BlockPos anchor = PlacementController.anchor();
        Vec3 camera = event.getCamera().getPosition();
        var pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        var registries = minecraft.level.registryAccess();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        for (BuildStep step : blueprint.queue(rotation)) {
            renderCell(minecraft, pose, buffer, blueprint, step.primary(), anchor, rotation, registries);
            if (step.secondary() != null) {
                renderCell(minecraft, pose, buffer, blueprint, step.secondary(), anchor, rotation, registries);
            }
        }
        buffer.endBatch();
        pose.popPose();
    }

    private static void renderCell(Minecraft minecraft, PoseStack pose, MultiBufferSource buffer, LoadedBlueprint blueprint, StructureCell cell, BlockPos anchor, Rotation rotation, net.minecraft.core.HolderLookup.Provider registries) {
        BlockPos pos = anchor.offset(cell.x(), cell.y(), cell.z());
        BlockState current = minecraft.level.getBlockState(pos);
        BlockState desired = blueprint.stateFor(cell, rotation, registries);
        boolean valid = current.equals(desired) || ReplaceableBlocks.isReplaceable(current);
        pose.pushPose();
        if (valid) {
            pose.translate(pos.getX() + 0.08D, pos.getY() + 0.08D, pos.getZ() + 0.08D);
            pose.scale(0.84F, 0.84F, 0.84F);
            minecraft.getBlockRenderer().renderSingleBlock(desired, pose, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        } else {
            pose.translate(pos.getX(), pos.getY(), pos.getZ());
            LevelRenderer.renderLineBox(pose, buffer.getBuffer(RenderType.lines()), new AABB(0, 0, 0, 1, 1, 1), 1.0F, 0.15F, 0.15F, 1.0F);
        }
        pose.popPose();
    }
}
