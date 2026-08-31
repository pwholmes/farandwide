package com.lastcallsoftware.farandwide.route.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Requests an explicit active state for one persistent vehicle assignment. */
public record VehicleActivationMutationPayload(int assigneeId, boolean active) implements CustomPacketPayload {
    public static final Type<VehicleActivationMutationPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("farandwide", "vehicle_activation_mutation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VehicleActivationMutationPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeVarInt(payload.assigneeId);
                        buffer.writeBoolean(payload.active);
                    },
                    buffer -> new VehicleActivationMutationPayload(buffer.readVarInt(), buffer.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
