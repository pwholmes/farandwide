package com.lastcallsoftware.farandwide.route.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import com.lastcallsoftware.farandwide.route.Route;
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
}
