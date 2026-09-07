package com.lastcallsoftware.farandwide.route;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class RouteMetricsTest {
    private static final Route ROUTE = new Route(1, "Climb", TraversalType.ONE_WAY, List.of(
            new Waypoint(new Vec3(0, 0, 0), Waypoint.DEFAULT_DIMENSION),
            new Waypoint(new Vec3(0, 3, 4), Waypoint.DEFAULT_DIMENSION),
            new Waypoint(new Vec3(0, 3, 8), Waypoint.DEFAULT_DIMENSION)));

    @Test
    void remainingDistanceUsesThreeDimensionalSegmentsTowardIncreasingWaypoints() {
        assertEquals(11.0, RouteMetrics.remainingDistance(ROUTE, new Vec3(0, -2, 0), 0, 1));
    }

    @Test
    void remainingDistanceFollowsDecreasingWaypointDirection() {
        assertEquals(11.0, RouteMetrics.remainingDistance(ROUTE, new Vec3(0, 3, 10), 2, -1));
    }

    @Test
    void invalidTargetHasNoRemainingDistance() {
        assertEquals(0.0, RouteMetrics.remainingDistance(ROUTE, Vec3.ZERO, 3, 1));
    }
}
