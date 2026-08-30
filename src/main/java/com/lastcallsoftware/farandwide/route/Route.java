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
}
