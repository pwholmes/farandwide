package com.lastcallsoftware.farandwide.route.server;

import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.route.CargoBehavior;
import com.lastcallsoftware.farandwide.route.CargoOperation;
import com.lastcallsoftware.farandwide.route.TraversalType;
import com.lastcallsoftware.farandwide.route.Waypoint;
import com.lastcallsoftware.farandwide.route.WaypointAction;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideSavedData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    void oneWayProcessesCargoOnceBeforeAdvancing() {
        Fixture fixture = cargoFixture(TraversalType.ONE_WAY, 2);
        AtomicInteger processed = new AtomicInteger();
        RouteAssignment assignment = fixture.data.getAssignment(fixture.assigneeId);
        Waypoint target = fixture.route.getWaypoints().get(assignment.getTargetWaypointIndex());

        assertTrue(ServerRouteTraversalController.processArrival(
                fixture.data, fixture.assigneeId, fixture.route, assignment, target,
                behavior -> processed.incrementAndGet()));

        assertEquals(1, processed.get());
        assertEquals(1, fixture.data.getAssignment(fixture.assigneeId).getTargetWaypointIndex());
    }

    @Test
    void loopProcessesCargoBeforeWrapping() {
        Fixture fixture = cargoFixture(TraversalType.LOOP, 2);
        fixture.data.updateAssignmentProgress(fixture.assigneeId, 1, 1);
        AtomicInteger processed = new AtomicInteger();
        RouteAssignment assignment = fixture.data.getAssignment(fixture.assigneeId);

        ServerRouteTraversalController.processArrival(
                fixture.data, fixture.assigneeId, fixture.route, assignment,
                fixture.route.getWaypoints().get(1), behavior -> processed.incrementAndGet());

        assertEquals(1, processed.get());
        assertEquals(0, fixture.data.getAssignment(fixture.assigneeId).getTargetWaypointIndex());
    }

    @Test
    void reverseProcessesCargoInBothDirections() {
        Fixture fixture = cargoFixture(TraversalType.REVERSE, 3);
        AtomicInteger processed = new AtomicInteger();

        fixture.data.updateAssignmentProgress(fixture.assigneeId, 2, 1);
        RouteAssignment forward = fixture.data.getAssignment(fixture.assigneeId);
        ServerRouteTraversalController.processArrival(
                fixture.data, fixture.assigneeId, fixture.route, forward,
                fixture.route.getWaypoints().get(2), behavior -> processed.incrementAndGet());

        fixture.data.updateAssignmentProgress(fixture.assigneeId, 0, -1);
        RouteAssignment backward = fixture.data.getAssignment(fixture.assigneeId);
        ServerRouteTraversalController.processArrival(
                fixture.data, fixture.assigneeId, fixture.route, backward,
                fixture.route.getWaypoints().get(0), behavior -> processed.incrementAndGet());

        assertEquals(2, processed.get());
        assertEquals(1, fixture.data.getAssignment(fixture.assigneeId).getTraversalDirection());
    }

    @Test
    void normalWaypointAdvancesWithoutCargoProcessing() {
        Fixture fixture = fixture(TraversalType.ONE_WAY, 2);
        AtomicInteger processed = new AtomicInteger();
        RouteAssignment assignment = fixture.data.getAssignment(fixture.assigneeId);

        ServerRouteTraversalController.processArrival(
                fixture.data, fixture.assigneeId, fixture.route, assignment,
                fixture.route.getWaypoints().getFirst(), behavior -> processed.incrementAndGet());

        assertEquals(0, processed.get());
        assertEquals(1, fixture.data.getAssignment(fixture.assigneeId).getTargetWaypointIndex());
    }

    @Test
    void cargoArrivalStillAdvancesWhenNoStationCanBeResolved() {
        Fixture fixture = cargoFixture(TraversalType.ONE_WAY, 2);
        RouteAssignment assignment = fixture.data.getAssignment(fixture.assigneeId);

        assertTrue(ServerRouteTraversalController.processArrival(
                fixture.data, fixture.assigneeId, fixture.route, assignment,
                fixture.route.getWaypoints().getFirst(), behavior -> {
                    // Missing station or unsupported cargo vehicle: no transfer occurs.
                }));

        assertEquals(1, fixture.data.getAssignment(fixture.assigneeId).getTargetWaypointIndex());
        assertTrue(fixture.data.getAssignment(fixture.assigneeId).isActive());
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
        data.setAssignmentActive(assigneeId, true);
        assignment = data.getAssignment(assigneeId);
        return new Fixture(data, data.getRoute(route.getId()), assigneeId, assignment);
    }

    private static Fixture cargoFixture(TraversalType traversalType, int waypointCount) {
        Fixture fixture = fixture(traversalType, waypointCount);
        for (Waypoint waypoint : fixture.route.getWaypoints()) {
            fixture.data.convertWaypoint(fixture.route.getId(), waypoint.id(),
                    WaypointAction.cargo(CargoBehavior.unfiltered(CargoOperation.LOAD)));
        }
        return new Fixture(fixture.data, fixture.data.getRoute(fixture.route.getId()),
                fixture.assigneeId, fixture.data.getAssignment(fixture.assigneeId));
    }

    private record Fixture(FarAndWideSavedData data, Route route, int assigneeId, RouteAssignment assignment) {
    }
}
