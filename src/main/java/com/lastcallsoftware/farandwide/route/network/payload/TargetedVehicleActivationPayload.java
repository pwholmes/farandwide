package com.lastcallsoftware.farandwide.route.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Requests activation toggling for a looked-at vehicle. */
public record TargetedVehicleActivationPayload(int entityId) implements CustomPacketPayload {
    public static final Type<TargetedVehicleActivationPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("farandwide", "targeted_vehicle_activation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TargetedVehicleActivationPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeVarInt(payload.entityId),
                    buffer -> new TargetedVehicleActivationPayload(buffer.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
