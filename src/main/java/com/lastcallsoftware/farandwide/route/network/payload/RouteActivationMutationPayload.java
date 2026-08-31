package com.lastcallsoftware.farandwide.route.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Requests an explicit active state for every assignment on one route. */
public record RouteActivationMutationPayload(int routeId, boolean active) implements CustomPacketPayload {
    public static final Type<RouteActivationMutationPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("farandwide", "route_activation_mutation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RouteActivationMutationPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeVarInt(payload.routeId);
                        buffer.writeBoolean(payload.active);
                    },
                    buffer -> new RouteActivationMutationPayload(buffer.readVarInt(), buffer.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
