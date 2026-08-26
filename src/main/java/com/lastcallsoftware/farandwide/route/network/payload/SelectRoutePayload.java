package com.lastcallsoftware.farandwide.route.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Persists the sender's selected route. */
public record SelectRoutePayload(int routeId) implements CustomPacketPayload {
    public static final Type<SelectRoutePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("farandwide", "select_route"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectRoutePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeVarInt(payload.routeId),
            buffer -> new SelectRoutePayload(buffer.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
