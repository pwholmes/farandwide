package com.lastcallsoftware.farandwide.route.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Requests removal of one persistent vehicle assignment. */
public record VehicleUnassignmentMutationPayload(int assigneeId) implements CustomPacketPayload {
    public static final Type<VehicleUnassignmentMutationPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("farandwide", "vehicle_unassignment_mutation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VehicleUnassignmentMutationPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeVarInt(payload.assigneeId),
                    buffer -> new VehicleUnassignmentMutationPayload(buffer.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
