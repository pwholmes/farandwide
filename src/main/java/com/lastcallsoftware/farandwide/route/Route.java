package com.lastcallsoftware.farandwide.route;

import java.util.List;

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
public record Route(int id, String name, TraversalType traversalType, List<Waypoint> waypoints) {
    /** Distance in front of the player at which a newly requested waypoint is placed. */
    public static final double OFFSET_DISTANCE = 1.0;

    public Route {
        waypoints = List.copyOf(waypoints);
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public TraversalType getTraversalType() { return traversalType; }
    public List<Waypoint> getWaypoints() { return waypoints; }
}
