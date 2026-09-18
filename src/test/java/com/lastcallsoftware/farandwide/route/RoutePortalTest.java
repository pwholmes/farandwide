package com.lastcallsoftware.farandwide.route;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class RoutePortalTest {
    private static final Identifier OVERWORLD = Identifier.parse("minecraft:overworld");
    private static final Identifier NETHER = Identifier.parse("minecraft:the_nether");

    @Test
    void adjacentPortalWaypointsPairInBothDirections() {
        Route route = new Route(1, "Crossing", TraversalType.REVERSE, List.of(
                new Waypoint(Vec3.ZERO, OVERWORLD),
                new Waypoint(1, new Vec3(1, 64, 1), OVERWORLD, WaypointAction.portal()),
                new Waypoint(2, new Vec3(8, 64, 8), NETHER, WaypointAction.portal()),
                new Waypoint(new Vec3(10, 64, 10), NETHER)));

        assertEquals(2, route.portalExitIndex(1, 1));
        assertEquals(1, route.portalExitIndex(2, -1));
        assertEquals(2, route.portalEntranceIndex(1, -1, TraversalType.REVERSE));
        assertEquals(1, route.portalEntranceIndex(2, 1, TraversalType.REVERSE));
        assertEquals(-1, route.portalExitIndex(0, 1));
        assertEquals(-1, route.portalExitIndex(2, 1));
    }

    @Test
    void loopCanCrossFromLastWaypointToFirst() {
        Route route = new Route(2, "Loop", TraversalType.LOOP, List.of(
                new Waypoint(1, Vec3.ZERO, OVERWORLD, WaypointAction.portal()),
                new Waypoint(2, Vec3.ZERO, NETHER, WaypointAction.portal())));

        assertEquals(0, route.portalExitIndex(1, 1));
        assertEquals(1, route.portalEntranceIndex(0, 1, TraversalType.LOOP));
    }

    @Test
    void crossingRequiresPortalWaypointsInDifferentDimensions() {
        Route route = new Route(1, "Invalid crossing", TraversalType.ONE_WAY, List.of(
                new Waypoint(1, Vec3.ZERO, OVERWORLD, WaypointAction.portal()),
                new Waypoint(2, Vec3.ZERO, OVERWORLD, WaypointAction.portal()),
                new Waypoint(3, Vec3.ZERO, NETHER, WaypointAction.normal())));

        assertEquals(-1, route.portalExitIndex(0, 1));
        assertEquals(-1, route.portalExitIndex(1, 1));
    }
}
