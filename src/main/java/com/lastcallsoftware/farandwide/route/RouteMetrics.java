package com.lastcallsoftware.farandwide.route;

import java.util.List;
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
        if (targetWaypointIndex < 0 || targetWaypointIndex >= waypoints.size()) {
            return 0.0;
        }

        double distance = position.distanceTo(waypoints.get(targetWaypointIndex).position());
        int step = direction < 0 ? -1 : 1;
        for (int index = targetWaypointIndex + step;
                index >= 0 && index < waypoints.size();
                index += step) {
            distance += waypoints.get(index - step).position().distanceTo(waypoints.get(index).position());
        }
        return distance;
    }
}
