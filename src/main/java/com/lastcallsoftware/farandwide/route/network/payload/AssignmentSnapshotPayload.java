package com.lastcallsoftware.farandwide.route.network.payload;

import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.route.TraversalType;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server snapshot of the assignment relevant to one client.
 *
 * <p>The outer {@code entityId} is Minecraft's current runtime ID. The persisted
 * stable assignee ID is deliberately not written to the wire. During decoding,
 * the assignment is reconstructed with {@code entityId} as its assignee field so
 * client lookup code can use {@code ClientLevel#getEntity(int)} semantics. This
 * translation is covered by a wire-codec test and must not be removed casually.
 *
 * <p>A null assignment is encoded explicitly and tells the client to remove any
 * cached assignment for that runtime entity.
 */
public record AssignmentSnapshotPayload(int entityId, RouteAssignment assignment) implements CustomPacketPayload {
    public static final Type<AssignmentSnapshotPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("farandwide", "assignment_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssignmentSnapshotPayload> STREAM_CODEC = StreamCodec.of(
            AssignmentSnapshotPayload::write, AssignmentSnapshotPayload::read);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(RegistryFriendlyByteBuf buffer, AssignmentSnapshotPayload payload) {
        buffer.writeVarInt(payload.entityId);
        buffer.writeBoolean(payload.assignment != null);
        if (payload.assignment == null) return;
        RouteAssignment value = payload.assignment;
        buffer.writeVarInt(value.getRouteId());
        buffer.writeVarInt(value.getTargetWaypointIndex());
        buffer.writeVarInt(value.getTraversalDirection());
        buffer.writeBoolean(value.getTraversalTypeOverride() != null);
        if (value.getTraversalTypeOverride() != null) buffer.writeVarInt(value.getTraversalTypeOverride().ordinal());
        buffer.writeBoolean(value.isActive());
    }

    private static AssignmentSnapshotPayload read(RegistryFriendlyByteBuf buffer) {
        int entityId = buffer.readVarInt();
        if (!buffer.readBoolean()) return new AssignmentSnapshotPayload(entityId, null);
        int routeId = buffer.readVarInt();
        int target = buffer.readVarInt();
        int direction = buffer.readVarInt();
        TraversalType override = null;
        if (buffer.readBoolean()) {
            int ordinal = buffer.readVarInt();
            if (ordinal < 0 || ordinal >= TraversalType.values().length) throw new IllegalArgumentException("Invalid traversal type");
            override = TraversalType.values()[ordinal];
        }
        return new AssignmentSnapshotPayload(entityId,
                new RouteAssignment(routeId, entityId, target, direction, override, buffer.readBoolean()));
    }
}
