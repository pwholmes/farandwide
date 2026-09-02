package com.lastcallsoftware.farandwide.route;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * Read-only management view of one persistent vehicle or player route assignment.
 *
 * <p>The stable assignee ID is used for mutations. The display name is only a
 * human-readable label and is never used as an identity key.
 */
public record VehicleRouteAssignment(
        int assigneeId,
        int routeId,
        String displayName,
        int targetWaypointIndex,
        int traversalDirection,
        boolean active,
        Optional<Position> position) {

    public VehicleRouteAssignment(int assigneeId, int routeId, String displayName, int targetWaypointIndex) {
        this(assigneeId, routeId, displayName, targetWaypointIndex, 1, false, Optional.empty());
    }

    public VehicleRouteAssignment(
            int assigneeId, int routeId, String displayName, int targetWaypointIndex, boolean active) {
        this(assigneeId, routeId, displayName, targetWaypointIndex, 1, active, Optional.empty());
    }

    public VehicleRouteAssignment(
            int assigneeId, int routeId, String displayName, int targetWaypointIndex,
            int traversalDirection, boolean active) {
        this(assigneeId, routeId, displayName, targetWaypointIndex, traversalDirection, active, Optional.empty());
    }

    public VehicleRouteAssignment {
        if (traversalDirection != -1 && traversalDirection != 1) {
            throw new IllegalArgumentException("Traversal direction must be -1 or 1");
        }
    }

    public VehicleRouteAssignment withPosition(Identifier dimension, BlockPos blockPosition) {
        return new VehicleRouteAssignment(assigneeId, routeId, displayName, targetWaypointIndex, traversalDirection,
                active,
                Optional.of(new Position(dimension, blockPosition, true)));
    }

    public VehicleRouteAssignment withLastKnownPosition(Identifier dimension, BlockPos blockPosition) {
        return new VehicleRouteAssignment(assigneeId, routeId, displayName, targetWaypointIndex, traversalDirection,
                active,
                Optional.of(new Position(dimension, blockPosition, false)));
    }

    public VehicleRouteAssignment withDisplayName(String name) {
        return new VehicleRouteAssignment(
                assigneeId, routeId, name, targetWaypointIndex, traversalDirection, active, position);
    }

    /** Loaded vehicle location captured when the management snapshot was built. */
    public record Position(Identifier dimension, BlockPos blockPosition, boolean current) {
    }
}
