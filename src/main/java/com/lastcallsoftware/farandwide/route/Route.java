package com.lastcallsoftware.farandwide.route;

import java.util.List;
import java.util.Objects;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The complete persisted definition of a route.
 *
 * <p>A route ID is allocated by {@code FarAndWideSavedData} and remains stable
 * across world reloads. Waypoints include their dimension, so their list order
 * is meaningful even when consecutive waypoints are in different dimensions.
 *
 * <p>This record is immutable to prevent client caches, screens, or renderers
 * from accidentally changing server-owned state. Authoritative edits replace
 * the stored record through {@code FarAndWideSavedData}, which also marks the
 * world data dirty. The defensive {@link List#copyOf(java.util.Collection)} is
 * important: without it, callers could still mutate the waypoint list behind
 * the record's back.
 */
@NonNullByDefault
public record Route(int id, String name, TraversalType traversalType, List<Waypoint> waypoints) {
    public Route {
        name = Objects.requireNonNull(name, "name");
        traversalType = Objects.requireNonNull(traversalType, "traversalType");
        waypoints = List.copyOf(waypoints);
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public TraversalType getTraversalType() { return traversalType; }
    public List<Waypoint> getWaypoints() { return waypoints; }

    /** Returns the adjacent portal exit in the current traversal direction, if paired. */
    public int portalExitIndex(int entranceIndex, int direction) {
        return portalExitIndex(entranceIndex, direction, traversalType);
    }

    public int portalExitIndex(int entranceIndex, int direction, TraversalType activeTraversalType) {
        if (entranceIndex < 0 || entranceIndex >= waypoints.size()
                || !(waypoints.get(entranceIndex).action() instanceof WaypointAction.Portal)) {
            return -1;
        }
        int exitIndex = entranceIndex + (direction < 0 ? -1 : 1);
        if (activeTraversalType == TraversalType.LOOP && waypoints.size() > 1) {
            exitIndex = Math.floorMod(exitIndex, waypoints.size());
        }
        if (exitIndex < 0 || exitIndex >= waypoints.size()) {
            return -1;
        }
        Waypoint entrance = waypoints.get(entranceIndex);
        Waypoint exit = waypoints.get(exitIndex);
        return exit.action() instanceof WaypointAction.Portal
                && !entrance.dimension().equals(exit.dimension()) ? exitIndex : -1;
    }

    /** Returns the portal to approach when the current target is across a crossing. */
    public int portalEntranceIndex(int targetIndex, int direction, TraversalType activeTraversalType) {
        if (targetIndex < 0 || targetIndex >= waypoints.size()) {
            return -1;
        }
        int entranceIndex = targetIndex - (direction < 0 ? -1 : 1);
        if (activeTraversalType == TraversalType.LOOP && waypoints.size() > 1) {
            entranceIndex = Math.floorMod(entranceIndex, waypoints.size());
        }
        return entranceIndex >= 0 && entranceIndex < waypoints.size()
                && portalExitIndex(entranceIndex, direction, activeTraversalType) == targetIndex
                        ? entranceIndex : -1;
    }

}
