package com.lastcallsoftware.farandwide.route.server;

import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.route.CargoBehavior;
import com.lastcallsoftware.farandwide.route.CargoOrder;
import com.lastcallsoftware.farandwide.route.CargoStationBinding;
import com.lastcallsoftware.farandwide.route.CargoOperation;
import com.lastcallsoftware.farandwide.route.OrderLeg;
import com.lastcallsoftware.farandwide.route.OrderLine;
import com.lastcallsoftware.farandwide.route.RouteOperationResult;
import com.lastcallsoftware.farandwide.route.TraversalType;
import com.lastcallsoftware.farandwide.route.Waypoint;
import com.lastcallsoftware.farandwide.route.WaypointAction;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideSavedData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.UUID;

class ServerRouteTraversalControllerTest {
    private static final Identifier OVERWORLD = Identifier.parse("minecraft:overworld");

    @SuppressWarnings("deprecation")
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (var item : List.of(Items.AIR, Items.GOLD_INGOT)) {
            item.builtInRegistryHolder().bindComponents(DataComponents.COMMON_ITEM_COMPONENTS);
        }
    }

    @Test
    void oneWayStopsAfterFinalWaypointAndPreparesReverseLeg() {
        Fixture fixture = fixture(TraversalType.ONE_WAY, 3);
        fixture.data.updateAssignmentProgress(fixture.assigneeId, 2, 1);

        assertTrue(ServerRouteTraversalController.advanceAssignment(
                fixture.data, fixture.assigneeId, fixture.route, fixture.data.getAssignment(fixture.assigneeId)));

        RouteAssignment result = fixture.data.getAssignment(fixture.assigneeId);
        assertFalse(result.isActive());
        assertEquals(2, result.getTargetWaypointIndex());
        assertEquals(1, result.getTraversalDirection());
        assertTrue(result.isRestartAnchor());
    }

    @Test
    void oneWayStopsAfterFirstWaypointAndPreparesForwardLeg() {
        Fixture fixture = fixture(TraversalType.ONE_WAY, 3);
        fixture.data.updateAssignmentProgress(fixture.assigneeId, 0, -1);

        assertTrue(ServerRouteTraversalController.advanceAssignment(
                fixture.data, fixture.assigneeId, fixture.route, fixture.data.getAssignment(fixture.assigneeId)));

        RouteAssignment result = fixture.data.getAssignment(fixture.assigneeId);
        assertFalse(result.isActive());
        assertEquals(0, result.getTargetWaypointIndex());
        assertEquals(-1, result.getTraversalDirection());
        assertTrue(result.isRestartAnchor());
    }

    @Test
    void oneWayContinuesBackwardBetweenEndpoints() {
        Fixture fixture = fixture(TraversalType.ONE_WAY, 3);
        fixture.data.updateAssignmentProgress(fixture.assigneeId, 1, -1);

        assertTrue(ServerRouteTraversalController.advanceAssignment(
                fixture.data, fixture.assigneeId, fixture.route, fixture.data.getAssignment(fixture.assigneeId)));

        RouteAssignment result = fixture.data.getAssignment(fixture.assigneeId);
        assertTrue(result.isActive());
        assertEquals(0, result.getTargetWaypointIndex());
        assertEquals(-1, result.getTraversalDirection());
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
    void oneWayReversesAtEndpointsWhileOrderCargoIsOutstanding() {
        Fixture fixture = fixture(TraversalType.ONE_WAY, 3);
        fixture.data.updateAssignmentProgress(fixture.assigneeId, 2, 1);
        addOutstandingOrder(fixture);

        assertTrue(ServerRouteTraversalController.advanceAssignment(
                fixture.data, fixture.assigneeId, fixture.route, fixture.data.getAssignment(fixture.assigneeId)));

        RouteAssignment result = fixture.data.getAssignment(fixture.assigneeId);
        assertTrue(result.isActive());
        assertEquals(1, result.getTargetWaypointIndex());
        assertEquals(-1, result.getTraversalDirection());
    }

    @Test
    void oneWayStopsAtEndpointAfterItsOutstandingOrderIsCancelled() {
        Fixture fixture = fixture(TraversalType.ONE_WAY, 3);
        fixture.data.updateAssignmentProgress(fixture.assigneeId, 2, 1);
        UUID owner = addOutstandingOrder(fixture);
        CargoOrder order = fixture.data.getOrders().getFirst();
        assertTrue(fixture.data.cancelOrder(order.id(), owner));

        assertTrue(ServerRouteTraversalController.advanceAssignment(
                fixture.data, fixture.assigneeId, fixture.route, fixture.data.getAssignment(fixture.assigneeId)));

        assertFalse(fixture.data.getAssignment(fixture.assigneeId).isActive());
    }

    @Test
    void oneWayEndpointCargoKeepsEndpointAsRestartTarget() {
        Fixture fixture = cargoFixture(TraversalType.ONE_WAY, 3);
        fixture.data.updateAssignmentProgress(fixture.assigneeId, 2, 1);
        AtomicInteger processed = new AtomicInteger();
        RouteAssignment assignment = fixture.data.getAssignment(fixture.assigneeId);

        assertTrue(ServerRouteTraversalController.processArrival(
                fixture.data, fixture.assigneeId, fixture.route, assignment,
                fixture.route.getWaypoints().get(2), behavior -> processed.incrementAndGet()));

        RouteAssignment result = fixture.data.getAssignment(fixture.assigneeId);
        assertEquals(1, processed.get());
        assertFalse(result.isActive());
        assertEquals(2, result.getTargetWaypointIndex());
        assertEquals(1, result.getTraversalDirection());
    }

    @Test
    void reverseDwellsOnlyAtTheEndpointBeforeItChangesDirection() {
        Fixture fixture = fixture(TraversalType.REVERSE, 3);

        fixture.data.updateAssignmentProgress(fixture.assigneeId, 2, 1);
        assertTrue(ServerRouteTraversalController.reversesAtTarget(
                fixture.route, fixture.data.getAssignment(fixture.assigneeId)));

        fixture.data.updateAssignmentProgress(fixture.assigneeId, 1, 1);
        assertFalse(ServerRouteTraversalController.reversesAtTarget(
                fixture.route, fixture.data.getAssignment(fixture.assigneeId)));

        fixture.data.setTraversalType(fixture.route.id(), TraversalType.LOOP);
        fixture.data.updateAssignmentProgress(fixture.assigneeId, 2, 1);
        assertFalse(ServerRouteTraversalController.reversesAtTarget(
                fixture.data.getRoute(fixture.route.id()), fixture.data.getAssignment(fixture.assigneeId)));
    }

    @Test
    void oneWayRestartAnchorProcessesCargoAgainAndBeginsReverseLeg() {
        Fixture fixture = cargoFixture(TraversalType.ONE_WAY, 3);
        fixture.data.updateAssignmentProgress(fixture.assigneeId, 2, 1);
        AtomicInteger processed = new AtomicInteger();
        RouteAssignment finalApproach = fixture.data.getAssignment(fixture.assigneeId);
        ServerRouteTraversalController.processArrival(
                fixture.data, fixture.assigneeId, fixture.route, finalApproach,
                fixture.route.getWaypoints().get(2), behavior -> processed.incrementAndGet());
        fixture.data.setAssignmentActive(fixture.assigneeId, true);

        RouteAssignment restart = fixture.data.getAssignment(fixture.assigneeId);
        assertTrue(ServerRouteTraversalController.processArrival(
                fixture.data, fixture.assigneeId, fixture.route, restart,
                fixture.route.getWaypoints().get(2), behavior -> processed.incrementAndGet()));

        RouteAssignment result = fixture.data.getAssignment(fixture.assigneeId);
        assertEquals(2, processed.get());
        assertTrue(result.isActive());
        assertEquals(1, result.getTargetWaypointIndex());
        assertEquals(-1, result.getTraversalDirection());
        assertFalse(result.isRestartAnchor());
    }

    @Test
    void oneWayRestartAtFirstWaypointProcessesCargoBeforeForwardLeg() {
        Fixture fixture = cargoFixture(TraversalType.ONE_WAY, 3);
        fixture.data.updateAssignmentProgress(fixture.assigneeId, 0, -1);
        AtomicInteger processed = new AtomicInteger();
        ServerRouteTraversalController.processArrival(
                fixture.data, fixture.assigneeId, fixture.route, fixture.data.getAssignment(fixture.assigneeId),
                fixture.route.getWaypoints().getFirst(), behavior -> processed.incrementAndGet());
        fixture.data.setAssignmentActive(fixture.assigneeId, true);

        assertTrue(ServerRouteTraversalController.processArrival(
                fixture.data, fixture.assigneeId, fixture.route, fixture.data.getAssignment(fixture.assigneeId),
                fixture.route.getWaypoints().getFirst(), behavior -> processed.incrementAndGet()));

        RouteAssignment result = fixture.data.getAssignment(fixture.assigneeId);
        assertEquals(2, processed.get());
        assertTrue(result.isActive());
        assertEquals(1, result.getTargetWaypointIndex());
        assertEquals(1, result.getTraversalDirection());
        assertFalse(result.isRestartAnchor());
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

    @Test
    void roundTripVisitsEveryWaypointInBothDirectionsThenCanRestart() {
        Fixture fixture = cargoFixture(TraversalType.ROUND_TRIP, 3);
        AtomicInteger processed = new AtomicInteger();
        for (int target : new int[] {0, 1, 2, 1, 0}) {
            RouteAssignment assignment = fixture.data.getAssignment(fixture.assigneeId);
            assertTrue(assignment.isActive());
            assertEquals(target, assignment.getTargetWaypointIndex());
            assertTrue(ServerRouteTraversalController.processArrival(
                    fixture.data, fixture.assigneeId, fixture.route, assignment,
                    fixture.route.getWaypoints().get(target), behavior -> processed.incrementAndGet()));
        }
        RouteAssignment stopped = fixture.data.getAssignment(fixture.assigneeId);
        assertFalse(stopped.isActive());
        assertEquals(0, stopped.getTargetWaypointIndex());
        assertTrue(stopped.isRestartAnchor());
        assertEquals(5, processed.get());

        fixture.data.setAssignmentActive(fixture.assigneeId, true);
        ServerRouteTraversalController.advanceAssignment(
                fixture.data, fixture.assigneeId, fixture.route, fixture.data.getAssignment(fixture.assigneeId));
        RouteAssignment restarted = fixture.data.getAssignment(fixture.assigneeId);
        assertTrue(restarted.isActive());
        assertEquals(1, restarted.getTargetWaypointIndex());
        assertEquals(1, restarted.getTraversalDirection());
        assertFalse(restarted.isRestartAnchor());
    }

    @Test
    void roundTripKeepsRunningUntilAllOrdersAreDeliveredThenReturnsHome() {
        Fixture fixture = fixture(TraversalType.ROUND_TRIP, 3);
        addOutstandingOrder(fixture);
        addOutstandingOrder(fixture);
        for (int delivered = 0; delivered < 2; delivered++) {
            fixture.data.updateAssignmentProgress(fixture.assigneeId, 0, -1);
            ServerRouteTraversalController.advanceAssignment(
                    fixture.data, fixture.assigneeId, fixture.route, fixture.data.getAssignment(fixture.assigneeId));
            assertTrue(fixture.data.getAssignment(fixture.assigneeId).isActive());
            assertEquals(1, fixture.data.getAssignment(fixture.assigneeId).getTraversalDirection());
            assertTrue(fixture.data.creditOrders(fixture.route.id(), fixture.route.waypoints().getLast().id(),
                    new CargoStationBinding(BlockPos.ZERO, Direction.UP), Identifier.parse("minecraft:gold_ingot"), 1));
        }
        assertFalse(fixture.data.hasOutstandingOrdersOnRoute(fixture.route.id()));
        fixture.data.updateAssignmentProgress(fixture.assigneeId, 2, 1);
        for (int target : new int[] {2, 1, 0}) {
            RouteAssignment assignment = fixture.data.getAssignment(fixture.assigneeId);
            assertTrue(assignment.isActive());
            assertEquals(target, assignment.getTargetWaypointIndex());
            ServerRouteTraversalController.advanceAssignment(fixture.data, fixture.assigneeId, fixture.route, assignment);
        }
        assertFalse(fixture.data.getAssignment(fixture.assigneeId).isActive());
    }

    @Test
    void roundTripDwellsAtFarEndButStopsAtHome() {
        Fixture fixture = fixture(TraversalType.ROUND_TRIP, 3);
        fixture.data.updateAssignmentProgress(fixture.assigneeId, 2, 1);
        assertTrue(ServerRouteTraversalController.reversesAtTarget(
                fixture.route, fixture.data.getAssignment(fixture.assigneeId)));
        fixture.data.updateAssignmentProgress(fixture.assigneeId, 0, -1);
        assertFalse(ServerRouteTraversalController.reversesAtTarget(
                fixture.route, fixture.data.getAssignment(fixture.assigneeId)));
    }

    @Test
    void roundTripWithOneWaypointStopsOnArrival() {
        Fixture fixture = fixture(TraversalType.ROUND_TRIP, 1);
        assertTrue(ServerRouteTraversalController.advanceAssignment(
                fixture.data, fixture.assigneeId, fixture.route, fixture.data.getAssignment(fixture.assigneeId)));
        assertFalse(fixture.data.getAssignment(fixture.assigneeId).isActive());
    }

    @Test
    void roundTripRetargetsDeletedEndpointsBeforeTurningOrStopping() {
        Fixture fixture = fixture(TraversalType.ROUND_TRIP, 4);
        fixture.data.updateAssignmentProgress(fixture.assigneeId, 3, 1);
        assertTrue(fixture.data.removeWaypoint(fixture.route.id(), 3));
        Route route = fixture.data.getRoute(fixture.route.id());
        assertEquals(2, fixture.data.getAssignment(fixture.assigneeId).getTargetWaypointIndex());
        ServerRouteTraversalController.advanceAssignment(
                fixture.data, fixture.assigneeId, route, fixture.data.getAssignment(fixture.assigneeId));
        assertEquals(-1, fixture.data.getAssignment(fixture.assigneeId).getTraversalDirection());
        fixture.data.updateAssignmentProgress(fixture.assigneeId, 0, -1);
        assertTrue(fixture.data.removeWaypoint(fixture.route.id(), 0));
        route = fixture.data.getRoute(fixture.route.id());
        assertEquals(0, fixture.data.getAssignment(fixture.assigneeId).getTargetWaypointIndex());
        assertTrue(fixture.data.getAssignment(fixture.assigneeId).isActive());
        ServerRouteTraversalController.advanceAssignment(
                fixture.data, fixture.assigneeId, route, fixture.data.getAssignment(fixture.assigneeId));
        assertFalse(fixture.data.getAssignment(fixture.assigneeId).isActive());
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

    private static UUID addOutstandingOrder(Fixture fixture) {
        UUID owner = UUID.randomUUID();
        List<Waypoint> waypoints = fixture.route.getWaypoints();
        fixture.data.addOrder(new CargoOrder(UUID.randomUUID(), owner, List.of(new OrderLeg(
                fixture.route.getId(), waypoints.getFirst().id(), waypoints.getLast().id(),
                new CargoStationBinding(BlockPos.ZERO, Direction.UP),
                List.of(new OrderLine(Identifier.parse("minecraft:gold_ingot"), 1, 0)), RouteOperationResult.SUCCESS))));
        return owner;
    }

    private record Fixture(FarAndWideSavedData data, Route route, int assigneeId, RouteAssignment assignment) {
    }
}
