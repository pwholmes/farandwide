package com.lastcallsoftware.farandwide.route;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.eclipse.jdt.annotation.NonNullByDefault;

/** Derived route measurements used by navigation displays. */
@NonNullByDefault
public final class RouteMetrics {
    private RouteMetrics() {
    }

    /**
     * Measures from the assignee to its target, then through every remaining
     * waypoint in the assignment's current direction.
     */
    public static double remainingDistance(Route route, Vec3 position, int targetWaypointIndex, int direction) {
        List<Waypoint> waypoints = route.getWaypoints();
        return remainingDistance(route, position,
                targetWaypointIndex >= 0 && targetWaypointIndex < waypoints.size()
                        ? waypoints.get(targetWaypointIndex).dimension() : Waypoint.DEFAULT_DIMENSION,
                targetWaypointIndex, direction);
    }

    /** Adds travel within each dimension without inventing a distance through a portal. */
    public static double remainingDistance(Route route, Vec3 position, Identifier currentDimension,
            int targetWaypointIndex, int direction) {
        List<Waypoint> waypoints = route.getWaypoints();
        if (targetWaypointIndex < 0 || targetWaypointIndex >= waypoints.size()) {
            return 0.0;
        }

        Waypoint target = waypoints.get(targetWaypointIndex);
        double distance = currentDimension.equals(target.dimension())
                ? position.distanceTo(target.position()) : 0.0;
        int step = direction < 0 ? -1 : 1;
        for (int index = targetWaypointIndex + step;
                index >= 0 && index < waypoints.size();
                index += step) {
            Waypoint previous = waypoints.get(index - step);
            Waypoint next = waypoints.get(index);
            if (previous.dimension().equals(next.dimension())) {
                distance += previous.position().distanceTo(next.position());
            }
        }
        return distance;
    }
}
