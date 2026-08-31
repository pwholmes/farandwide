package com.lastcallsoftware.farandwide.route.network.payload;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.route.VehicleRouteAssignment;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-to-client replacement snapshot of rows used by the Route Management screen. */
public record VehicleAssignmentsSnapshotPayload(List<VehicleRouteAssignment> assignments)
        implements CustomPacketPayload {
    public static final Type<VehicleAssignmentsSnapshotPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("farandwide", "vehicle_assignments_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VehicleAssignmentsSnapshotPayload> STREAM_CODEC =
            StreamCodec.of(VehicleAssignmentsSnapshotPayload::write, VehicleAssignmentsSnapshotPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, VehicleAssignmentsSnapshotPayload payload) {
        if (payload.assignments.size() > Constants.Network.MAX_VEHICLE_ASSIGNMENTS) {
            throw new IllegalArgumentException("Too many vehicle assignments to synchronize");
        }
        buffer.writeCollection(payload.assignments, (target, assignment) -> {
            target.writeVarInt(assignment.assigneeId());
            target.writeVarInt(assignment.routeId());
            target.writeUtf(assignment.displayName(), Constants.Network.MAX_VEHICLE_NAME_LENGTH);
            target.writeVarInt(assignment.targetWaypointIndex());
            target.writeBoolean(assignment.active());
            target.writeBoolean(assignment.position().isPresent());
            assignment.position().ifPresent(position -> {
                target.writeIdentifier(position.dimension());
                target.writeBlockPos(position.blockPosition());
                target.writeBoolean(position.current());
            });
        });
    }

    private static VehicleAssignmentsSnapshotPayload read(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > Constants.Network.MAX_VEHICLE_ASSIGNMENTS) {
            throw new IllegalArgumentException("Received too many vehicle assignments");
        }
        List<VehicleRouteAssignment> assignments = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            int assigneeId = buffer.readVarInt();
            int routeId = buffer.readVarInt();
            String displayName = buffer.readUtf(Constants.Network.MAX_VEHICLE_NAME_LENGTH);
            int targetWaypointIndex = buffer.readVarInt();
            boolean active = buffer.readBoolean();
            Optional<VehicleRouteAssignment.Position> position = buffer.readBoolean()
                    ? Optional.of(new VehicleRouteAssignment.Position(
                            buffer.readIdentifier(), buffer.readBlockPos(), buffer.readBoolean()))
                    : Optional.empty();
            assignments.add(new VehicleRouteAssignment(
                    assigneeId, routeId, displayName, targetWaypointIndex, active, position));
        }
        return new VehicleAssignmentsSnapshotPayload(assignments);
    }
}
