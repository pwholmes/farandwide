package com.lastcallsoftware.farandwide.route.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Requests moving one specific vehicle's next waypoint backward or forward. */
public record VehicleWaypointMutationPayload(int assigneeId, int delta) implements CustomPacketPayload {
    public static final Type<VehicleWaypointMutationPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("farandwide", "vehicle_waypoint_mutation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VehicleWaypointMutationPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeVarInt(payload.assigneeId);
                        buffer.writeInt(payload.delta);
                    },
                    buffer -> new VehicleWaypointMutationPayload(buffer.readVarInt(), buffer.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
