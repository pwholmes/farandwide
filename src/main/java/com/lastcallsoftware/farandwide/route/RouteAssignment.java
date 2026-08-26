package com.lastcallsoftware.farandwide.route;

/**
 * Persisted traversal progress for one player or vehicle.
 *
 * <p>{@code assigneeEntityId} is historical naming: on disk it contains the
 * stable assignee ID allocated by {@code FarAndWideSavedData}, not Minecraft's
 * runtime entity ID. Assignment snapshots deliberately replace it with the
 * current runtime entity ID so client code can look up the loaded entity.
 *
 * <p>{@code traversalDirection} is {@code 1} while moving toward increasing
 * waypoint indices and {@code -1} while reversing. It matters only for reverse
 * traversal. A null override means the assignment follows the route's own
 * traversal type.
 *
 * <p>Like {@link Route}, this is immutable so every permanent change must pass
 * through saved data and therefore cannot silently omit {@code setDirty()}.
 */
public record RouteAssignment(
        int routeId,
        int assigneeEntityId,
        int targetWaypointIndex,
        int traversalDirection,
        TraversalType traversalTypeOverride,
        boolean active) {

    public RouteAssignment(int routeId, int assigneeEntityId, int targetWaypointIndex) {
        this(routeId, assigneeEntityId, targetWaypointIndex, 1, null, true);
    }

    public int getRouteId() { return routeId; }
    public int getAssigneeEntityId() { return assigneeEntityId; }
    public int getTargetWaypointIndex() { return targetWaypointIndex; }
    public int getTraversalDirection() { return traversalDirection; }
    public TraversalType getTraversalTypeOverride() { return traversalTypeOverride; }
    public boolean isActive() { return active; }

    public TraversalType getTraversalType(Route route) {
        return traversalTypeOverride == null ? route.getTraversalType() : traversalTypeOverride;
    }
}
