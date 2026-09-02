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
 * <p>The outer {@code entityId} is Minecraft's current runtime ID. The stable
 * assignee ID is also retained so client presentation code can associate the
 * runtime entity with its persistent management identity. During decoding, the
 * assignment itself is reconstructed with {@code entityId} as its assignee field
 * so client lookup code can use {@code ClientLevel#getEntity(int)} semantics.
 * This translation is covered by a wire-codec test and must not be removed
 * casually.
 *
 * <p>A null assignment is encoded explicitly and tells the client to remove any
 * cached assignment for that runtime entity.
 */
public record AssignmentSnapshotPayload(int entityId, int stableAssigneeId, RouteAssignment assignment)
        implements CustomPacketPayload {
    public static final Type<AssignmentSnapshotPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("farandwide", "assignment_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssignmentSnapshotPayload> STREAM_CODEC = StreamCodec.of(
            AssignmentSnapshotPayload::write, AssignmentSnapshotPayload::read);

    public AssignmentSnapshotPayload(int entityId, RouteAssignment assignment) {
        this(entityId, assignment == null ? 0 : assignment.getAssigneeId(), assignment);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(RegistryFriendlyByteBuf buffer, AssignmentSnapshotPayload payload) {
        buffer.writeVarInt(payload.entityId);
        buffer.writeBoolean(payload.assignment != null);
        if (payload.assignment == null) return;
        RouteAssignment value = payload.assignment;
        buffer.writeVarInt(payload.stableAssigneeId);
        buffer.writeVarInt(value.getRouteId());
        buffer.writeVarInt(value.getTargetWaypointIndex());
        buffer.writeVarInt(value.getTraversalDirection());
        buffer.writeBoolean(value.getTraversalTypeOverride() != null);
        if (value.getTraversalTypeOverride() != null) buffer.writeVarInt(value.getTraversalTypeOverride().ordinal());
        buffer.writeBoolean(value.isActive());
        buffer.writeBoolean(value.isRestartAnchor());
    }

    private static AssignmentSnapshotPayload read(RegistryFriendlyByteBuf buffer) {
        int entityId = buffer.readVarInt();
        if (!buffer.readBoolean()) return new AssignmentSnapshotPayload(entityId, null);
        int stableAssigneeId = buffer.readVarInt();
        int routeId = buffer.readVarInt();
        int target = buffer.readVarInt();
        int direction = buffer.readVarInt();
        TraversalType override = null;
        if (buffer.readBoolean()) {
            int ordinal = buffer.readVarInt();
            if (ordinal < 0 || ordinal >= TraversalType.values().length) throw new IllegalArgumentException("Invalid traversal type");
            override = TraversalType.values()[ordinal];
        }
        boolean active = buffer.readBoolean();
        boolean restartAnchor = buffer.readBoolean();
        return new AssignmentSnapshotPayload(entityId, stableAssigneeId,
                new RouteAssignment(routeId, entityId, target, direction, override, active, restartAnchor));
    }
}
