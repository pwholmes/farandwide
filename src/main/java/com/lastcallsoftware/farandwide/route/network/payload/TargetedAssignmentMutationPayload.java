package com.lastcallsoftware.farandwide.route.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Requests assignment of the selected route to a looked-at vehicle. */
public record TargetedAssignmentMutationPayload(int routeId, int entityId) implements CustomPacketPayload {
    public static final Type<TargetedAssignmentMutationPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("farandwide", "targeted_assignment_mutation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TargetedAssignmentMutationPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeVarInt(payload.routeId);
                        buffer.writeVarInt(payload.entityId);
                    },
                    buffer -> new TargetedAssignmentMutationPayload(buffer.readVarInt(), buffer.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
