package com.lastcallsoftware.farandwide.route.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestAssignmentSnapshotPayload() implements CustomPacketPayload {
    public static final Type<RequestAssignmentSnapshotPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("farandwide", "request_assignment_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestAssignmentSnapshotPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestAssignmentSnapshotPayload());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
