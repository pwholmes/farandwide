package com.lastcallsoftware.farandwide.route.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.UUID;

import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.route.RouteOperationResult;
import com.lastcallsoftware.farandwide.route.TraversalType;
import com.lastcallsoftware.farandwide.route.Waypoint;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideSavedData;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class RouteServiceTest {
    private static final Identifier OVERWORLD = Identifier.parse("minecraft:overworld");

    @Test
    void deletingRouteStopsAssigneesBeforeRemovingTheirAssignments() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = data.createRoute();
        data.addWaypoint(route.getId(), new Waypoint(Vec3.ZERO, OVERWORLD));
        int assigneeId = data.allocateAssigneeId();
        data.assignRoute(route.getId(), assigneeId, Vec3.ZERO, OVERWORLD);
        AtomicBoolean stoppedWhileAssigned = new AtomicBoolean();

        assertTrue(RouteService.deleteRoute(data, route.getId(),
                () -> stoppedWhileAssigned.set(data.getAssignment(assigneeId) != null)));

        assertTrue(stoppedWhileAssigned.get());
        assertNull(data.getAssignment(assigneeId));
        assertNull(data.getRoute(route.getId()));
    }

    @Test
    void deletingUnknownRouteDoesNotStopAssignees() {
        AtomicBoolean stopped = new AtomicBoolean();

        assertFalse(RouteService.deleteRoute(new FarAndWideSavedData(), 1, () -> stopped.set(true)));

        assertFalse(stopped.get());
    }

    @Test
    void assigningAnAlreadyAssignedAssigneeRemovesItsAssignmentAndStopsIt() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = data.createRoute();
        data.addWaypoint(route.getId(), new Waypoint(Vec3.ZERO, OVERWORLD));
        int assigneeId = data.allocateAssigneeId();
        data.assignRoute(route.getId(), assigneeId, Vec3.ZERO, OVERWORLD);
        data.setAssignmentActive(assigneeId, true);
        AtomicBoolean stopped = new AtomicBoolean();

        assertTrue(RouteService.unassignRoute(data, assigneeId, () -> stopped.set(true)));

        assertTrue(stopped.get());
        assertNull(data.getAssignment(assigneeId));
        assertFalse(RouteService.unassignRoute(data, assigneeId, () -> stopped.set(true)));
    }

    @Test
    void managementMutationsAllowVehiclesAndTheRequestingPlayerButNotOtherPlayers() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = data.createRoute();
        data.addWaypoint(route.getId(), new Waypoint(Vec3.ZERO, OVERWORLD));
        int requestingPlayer = data.allocateAssigneeId();
        int otherPlayer = data.allocateAssigneeId();
        int vehicle = data.allocateAssigneeId();
        data.assignRoute(route.getId(), requestingPlayer, Vec3.ZERO, OVERWORLD);
        data.assignRoute(route.getId(), otherPlayer, Vec3.ZERO, OVERWORLD);
        data.assignRoute(route.getId(), vehicle, Vec3.ZERO, OVERWORLD);
        data.registerVehicle(UUID.fromString("30000000-0000-0000-0000-000000000001"), vehicle, "boat");

        assertTrue(RouteService.isManagedAssignee(data, requestingPlayer, requestingPlayer));
        assertTrue(RouteService.isManagedAssignee(data, vehicle, requestingPlayer));
        assertFalse(RouteService.isManagedAssignee(data, otherPlayer, requestingPlayer));
    }

    @Test
    void assigningTheExistingRouteTogglesTheAssignmentOff() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = routeWithWaypoint(data, OVERWORLD);
        int assigneeId = data.allocateAssigneeId();
        data.assignRoute(route.getId(), assigneeId, Vec3.ZERO, OVERWORLD);
        data.setAssignmentActive(assigneeId, true);
        AtomicBoolean stopped = new AtomicBoolean();

        assertEquals(RouteOperationResult.SUCCESS, RouteService.assignRoute(
                data, route.getId(), route.getId(), assigneeId, Vec3.ZERO, OVERWORLD,
                () -> stopped.set(true)));

        assertNull(data.getAssignment(assigneeId));
        assertTrue(stopped.get());
    }

    @Test
    void assigningWithNoSelectedRouteTogglesTheAssignmentOff() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = routeWithWaypoint(data, OVERWORLD);
        int assigneeId = data.allocateAssigneeId();
        data.assignRoute(route.getId(), assigneeId, Vec3.ZERO, OVERWORLD);

        assertEquals(RouteOperationResult.SUCCESS, RouteService.assignRoute(
                data, 0, 0, assigneeId, Vec3.ZERO, OVERWORLD, () -> {}));

        assertNull(data.getAssignment(assigneeId));
    }

    @Test
    void assigningADifferentSelectedRouteReplacesTheExistingRoute() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route routeA = routeWithWaypoint(data, OVERWORLD);
        Route routeB = routeWithWaypoint(data, OVERWORLD);
        int assigneeId = data.allocateAssigneeId();
        data.assignRoute(routeA.getId(), assigneeId, Vec3.ZERO, OVERWORLD);
        data.setAssignmentActive(assigneeId, true);
        AtomicBoolean stopped = new AtomicBoolean();

        assertEquals(RouteOperationResult.SUCCESS, RouteService.assignRoute(
                data, routeB.getId(), routeB.getId(), assigneeId, Vec3.ZERO, OVERWORLD,
                () -> stopped.set(true)));

        assertEquals(routeB.getId(), data.getAssignment(assigneeId).getRouteId());
        assertTrue(stopped.get());
    }

    @Test
    void failedReplacementPreservesTheExistingAssignment() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route routeA = routeWithWaypoint(data, OVERWORLD);
        Route routeB = routeWithWaypoint(data, Identifier.parse("minecraft:the_nether"));
        int assigneeId = data.allocateAssigneeId();
        data.assignRoute(routeA.getId(), assigneeId, Vec3.ZERO, OVERWORLD);
        data.setAssignmentActive(assigneeId, true);
        AtomicBoolean stopped = new AtomicBoolean();

        assertEquals(RouteOperationResult.NO_WAYPOINT_IN_DIMENSION, RouteService.assignRoute(
                data, routeB.getId(), routeB.getId(), assigneeId, Vec3.ZERO, OVERWORLD,
                () -> stopped.set(true)));

        assertEquals(routeA.getId(), data.getAssignment(assigneeId).getRouteId());
        assertTrue(data.getAssignment(assigneeId).isActive());
        assertFalse(stopped.get());
    }

    @Test
    void replacingDeathRouteStopsFollowersBeforeMovingItsWaypoint() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        UUID playerUuid = UUID.fromString("50000000-0000-0000-0000-000000000001");
        Vec3 oldDeath = new Vec3(4, 60, 8);
        Vec3 newDeath = new Vec3(20, 70, 30);
        Route original = RouteService.recordPlayerDeath(
                data, playerUuid, "Paul", oldDeath, OVERWORLD, routeId -> {});
        AtomicBoolean stoppedBeforeReplacement = new AtomicBoolean();

        Route replaced = RouteService.recordPlayerDeath(
                data, playerUuid, "Paul", newDeath, OVERWORLD,
                routeId -> stoppedBeforeReplacement.set(
                        data.getRoute(routeId).getWaypoints().getFirst().position().equals(oldDeath)));

        assertTrue(stoppedBeforeReplacement.get());
        assertEquals(original.getId(), replaced.getId());
        assertEquals(newDeath, replaced.getWaypoints().getFirst().position());
        assertEquals("Paul's Death Route", replaced.getName());
    }

    @Test
    void reversingDirectionRetargetsTheAdjacentWaypoint() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = data.createRoute();
        data.addWaypoint(route.getId(), new Waypoint(new Vec3(0, 0, 0), OVERWORLD));
        data.addWaypoint(route.getId(), new Waypoint(new Vec3(10, 0, 0), OVERWORLD));
        data.addWaypoint(route.getId(), new Waypoint(new Vec3(20, 0, 0), OVERWORLD));
        int assigneeId = data.allocateAssigneeId();
        data.assignRoute(route.getId(), assigneeId, new Vec3(5, 0, 0), OVERWORLD);
        data.updateAssignmentProgress(assigneeId, 2, 1);

        assertEquals(RouteOperationResult.SUCCESS, RouteService.reverseVehicleDirection(data, assigneeId));

        RouteAssignment reversed = data.getAssignment(assigneeId);
        assertEquals(-1, reversed.getTraversalDirection());
        assertEquals(1, reversed.getTargetWaypointIndex());
    }

    @Test
    void reversingLoopDirectionWrapsAtTheFirstWaypoint() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = data.createRoute();
        data.setTraversalType(route.getId(), TraversalType.LOOP);
        data.addWaypoint(route.getId(), new Waypoint(new Vec3(0, 0, 0), OVERWORLD));
        data.addWaypoint(route.getId(), new Waypoint(new Vec3(10, 0, 0), OVERWORLD));
        data.addWaypoint(route.getId(), new Waypoint(new Vec3(20, 0, 0), OVERWORLD));
        int assigneeId = data.allocateAssigneeId();
        data.assignRoute(route.getId(), assigneeId, Vec3.ZERO, OVERWORLD);
        data.updateAssignmentProgress(assigneeId, 0, 1);

        assertEquals(RouteOperationResult.SUCCESS, RouteService.reverseVehicleDirection(data, assigneeId));

        RouteAssignment reversed = data.getAssignment(assigneeId);
        assertEquals(-1, reversed.getTraversalDirection());
        assertEquals(2, reversed.getTargetWaypointIndex());
    }

    private static Route routeWithWaypoint(FarAndWideSavedData data, Identifier dimension) {
        Route route = data.createRoute();
        data.addWaypoint(route.getId(), new Waypoint(Vec3.ZERO, dimension));
        return data.getRoute(route.getId());
    }
}
