package com.lastcallsoftware.farandwide.route.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client request for the complete read-only route cache. */
public record RequestRouteSnapshotPayload() implements CustomPacketPayload {
    public static final Type<RequestRouteSnapshotPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("farandwide", "request_route_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestRouteSnapshotPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestRouteSnapshotPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
