package com.lastcallsoftware.farandwide.route.server;

import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.route.TraversalType;
import com.lastcallsoftware.farandwide.route.Waypoint;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideSavedData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ServerRouteTraversalControllerTest {
    private static final Identifier OVERWORLD = Identifier.parse("minecraft:overworld");

    @Test
    void oneWayStopsAfterFinalWaypoint() {
        Fixture fixture = fixture(TraversalType.ONE_WAY, 2);
        fixture.data.updateAssignmentProgress(fixture.assigneeId, 1, 1);

        assertTrue(ServerRouteTraversalController.advanceAssignment(
                fixture.data, fixture.assigneeId, fixture.route, fixture.data.getAssignment(fixture.assigneeId)));

        RouteAssignment result = fixture.data.getAssignment(fixture.assigneeId);
        assertFalse(result.isActive());
        assertEquals(1, result.getTargetWaypointIndex());
    }

    @Test
    void loopWrapsToFirstWaypoint() {
        Fixture fixture = fixture(TraversalType.LOOP, 3);
        fixture.data.updateAssignmentProgress(fixture.assigneeId, 2, 1);

        assertTrue(ServerRouteTraversalController.advanceAssignment(
                fixture.data, fixture.assigneeId, fixture.route, fixture.data.getAssignment(fixture.assigneeId)));

        RouteAssignment result = fixture.data.getAssignment(fixture.assigneeId);
        assertTrue(result.isActive());
        assertEquals(0, result.getTargetWaypointIndex());
        assertEquals(1, result.getTraversalDirection());
    }

    @Test
    void reverseChangesDirectionAtBothEnds() {
        Fixture fixture = fixture(TraversalType.REVERSE, 3);
        fixture.data.updateAssignmentProgress(fixture.assigneeId, 2, 1);

        assertTrue(ServerRouteTraversalController.advanceAssignment(
                fixture.data, fixture.assigneeId, fixture.route, fixture.data.getAssignment(fixture.assigneeId)));
        RouteAssignment result = fixture.data.getAssignment(fixture.assigneeId);
        assertEquals(1, result.getTargetWaypointIndex());
        assertEquals(-1, result.getTraversalDirection());

        fixture.data.updateAssignmentProgress(fixture.assigneeId, 0, -1);
        assertTrue(ServerRouteTraversalController.advanceAssignment(
                fixture.data, fixture.assigneeId, fixture.route, fixture.data.getAssignment(fixture.assigneeId)));
        result = fixture.data.getAssignment(fixture.assigneeId);
        assertEquals(1, result.getTargetWaypointIndex());
        assertEquals(1, result.getTraversalDirection());
    }

    private static Fixture fixture(TraversalType traversalType, int waypointCount) {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = data.createRoute();
        data.setTraversalType(route.getId(), traversalType);
        for (int index = 0; index < waypointCount; index++) {
            data.addWaypoint(route.getId(), new Waypoint(new Vec3(index * 10, 64, 0), OVERWORLD));
        }
        int assigneeId = data.allocateAssigneeId();
        RouteAssignment assignment = data.assignRoute(route.getId(), assigneeId, Vec3.ZERO, OVERWORLD);
        return new Fixture(data, data.getRoute(route.getId()), assigneeId, assignment);
    }

    private record Fixture(FarAndWideSavedData data, Route route, int assigneeId, RouteAssignment assignment) {
    }
}
