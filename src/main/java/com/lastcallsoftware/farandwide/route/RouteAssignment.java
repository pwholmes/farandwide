package com.lastcallsoftware.farandwide.route;

/**
 * The persisted link between a {@link Route} and the player or vehicle assigned
 * to follow it.
 *
 * <p>This record tracks the core relationship that makes Far and Wide's route
 * system useful: a recorded route becomes actionable when a route assignment
 * connects it to an assignee. In addition to identifying both sides of that
 * relationship, the assignment records the assignee's current traversal state
 * so autonomous navigation can continue across entity unloads and world
 * restarts.
 *
 * <p>There is at most one current assignment per stable assignee ID. Assigning
 * another route to the same player or vehicle replaces its previous assignment.
 *
 * <p>{@code assigneeId} contains the stable ID allocated by
 * {@code FarAndWideSavedData} when persisted. Assignment snapshots translate it
 * to the current runtime entity ID so client code can look up the loaded entity.
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
        int assigneeId,
        int targetWaypointIndex,
        int traversalDirection,
        TraversalType traversalTypeOverride,
        boolean active) {

    public RouteAssignment(int routeId, int assigneeId, int targetWaypointIndex) {
        this(routeId, assigneeId, targetWaypointIndex, 1, null, true);
    }

    public int getRouteId() { return routeId; }
    public int getAssigneeId() { return assigneeId; }
    public int getTargetWaypointIndex() { return targetWaypointIndex; }
    public int getTraversalDirection() { return traversalDirection; }
    public TraversalType getTraversalTypeOverride() { return traversalTypeOverride; }
    public boolean isActive() { return active; }

    public TraversalType getTraversalType(Route route) {
        return traversalTypeOverride == null ? route.getTraversalType() : traversalTypeOverride;
    }
}
