package com.blueprintbuilder.network;

import com.blueprintbuilder.BlueprintBuilderMod;
import com.blueprintbuilder.construction.PlacementValidator;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PlaceBlueprintPayload(ResourceLocation blueprintId, BlockPos anchor, int rotationOrdinal, boolean generateCamp) implements CustomPacketPayload {
    public static final Type<PlaceBlueprintPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BlueprintBuilderMod.MOD_ID, "place_blueprint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlaceBlueprintPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, PlaceBlueprintPayload::blueprintId,
            BlockPos.STREAM_CODEC, PlaceBlueprintPayload::anchor,
            ByteBufCodecs.VAR_INT, PlaceBlueprintPayload::rotationOrdinal,
            ByteBufCodecs.BOOL, PlaceBlueprintPayload::generateCamp,
            PlaceBlueprintPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlaceBlueprintPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PlacementValidator.place(player, payload);
            }
        });
    }
}
