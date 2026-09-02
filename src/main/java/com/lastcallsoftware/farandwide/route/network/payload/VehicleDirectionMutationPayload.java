package com.lastcallsoftware.farandwide.route.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Requests reversing one managed route assignment. */
public record VehicleDirectionMutationPayload(int assigneeId) implements CustomPacketPayload {
    public static final Type<VehicleDirectionMutationPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("farandwide", "vehicle_direction_mutation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VehicleDirectionMutationPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeVarInt(payload.assigneeId),
                    buffer -> new VehicleDirectionMutationPayload(buffer.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
