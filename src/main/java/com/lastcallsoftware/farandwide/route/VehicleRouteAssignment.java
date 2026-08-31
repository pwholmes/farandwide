package com.lastcallsoftware.farandwide.route;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * Read-only management view of one persistent vehicle route assignment.
 *
 * <p>The stable assignee ID is used for mutations. The display name is only a
 * human-readable label and is never used as an identity key.
 */
public record VehicleRouteAssignment(
        int assigneeId,
        int routeId,
        String displayName,
        int targetWaypointIndex,
        boolean active,
        Optional<Position> position) {

    public VehicleRouteAssignment(int assigneeId, int routeId, String displayName, int targetWaypointIndex) {
        this(assigneeId, routeId, displayName, targetWaypointIndex, false, Optional.empty());
    }

    public VehicleRouteAssignment(
            int assigneeId, int routeId, String displayName, int targetWaypointIndex, boolean active) {
        this(assigneeId, routeId, displayName, targetWaypointIndex, active, Optional.empty());
    }

    public VehicleRouteAssignment withPosition(Identifier dimension, BlockPos blockPosition) {
        return new VehicleRouteAssignment(assigneeId, routeId, displayName, targetWaypointIndex, active,
                Optional.of(new Position(dimension, blockPosition, true)));
    }

    public VehicleRouteAssignment withLastKnownPosition(Identifier dimension, BlockPos blockPosition) {
        return new VehicleRouteAssignment(assigneeId, routeId, displayName, targetWaypointIndex, active,
                Optional.of(new Position(dimension, blockPosition, false)));
    }

    /** Loaded vehicle location captured when the management snapshot was built. */
    public record Position(Identifier dimension, BlockPos blockPosition, boolean current) {
    }
}
