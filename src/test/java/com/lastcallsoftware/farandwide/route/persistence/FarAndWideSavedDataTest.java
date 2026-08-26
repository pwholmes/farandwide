package com.lastcallsoftware.farandwide.route.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.route.TraversalType;
import com.lastcallsoftware.farandwide.route.Waypoint;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class FarAndWideSavedDataTest {
    private static final Identifier OVERWORLD = Identifier.parse("minecraft:overworld");
    private static final Identifier NETHER = Identifier.parse("minecraft:the_nether");

    @Test
    void routeAndAssignmentFieldsSurviveDiskRoundTrip() {
        FarAndWideSavedData original = new FarAndWideSavedData();
        Route route = original.createRoute();
        assertTrue(original.renameRoute(route.getId(), "Supply Run"));
        assertTrue(original.setTraversalType(route.getId(), TraversalType.REVERSE));
        assertTrue(original.addWaypoint(route.getId(), new Waypoint(new Vec3(1.25, 64, -8.5), OVERWORLD)));
        assertTrue(original.addWaypoint(route.getId(), new Waypoint(new Vec3(4, 70.5, 12), NETHER)));
        route = original.getRoute(route.getId());

        int assigneeId = original.allocateAssigneeId();
        RouteAssignment assignment = original.assignRoute(route.getId(), assigneeId, new Vec3(4, 70, 12), NETHER);
        assertNotNull(assignment);
        assertTrue(original.setAssignmentTraversalTypeOverride(assigneeId, TraversalType.LOOP));
        assertTrue(original.updateAssignmentProgress(assigneeId, 1, -1));
        assertTrue(original.setAssignmentActive(assigneeId, false));
        assertTrue(original.setSelectedRouteId(assigneeId, route.getId()));

        FarAndWideSavedData restored = roundTrip(original);
        Route restoredRoute = restored.getRoute(route.getId());
        assertNotNull(restoredRoute);
        assertEquals("Supply Run", restoredRoute.getName());
        assertEquals(TraversalType.REVERSE, restoredRoute.getTraversalType());
        assertEquals(route.getWaypoints(), restoredRoute.getWaypoints());

        RouteAssignment restoredAssignment = restored.getAssignment(assigneeId);
        assertNotNull(restoredAssignment);
        assertEquals(route.getId(), restoredAssignment.getRouteId());
        assertEquals(assigneeId, restoredAssignment.getAssigneeId());
        assertEquals(1, restoredAssignment.getTargetWaypointIndex());
        assertEquals(-1, restoredAssignment.getTraversalDirection());
        assertEquals(TraversalType.LOOP, restoredAssignment.getTraversalTypeOverride());
        assertFalse(restoredAssignment.isActive());
        assertEquals(route.getId(), restored.getSelectedRouteId(assigneeId));
    }

    @Test
    void loadedAllocatorsStayAboveEveryRestoredId() {
        JsonElement saved = JsonParser.parseString("""
                {
                  "dataVersion": 1,
                  "nextRouteId": 1,
                  "nextAssigneeId": 1,
                  "routes": [{
                    "id": 40,
                    "name": "Restored",
                    "traversalType": "one_way",
                    "waypoints": []
                  }],
                  "assignments": [{
                    "assigneeId": 70,
                    "assignment": {
                      "routeId": 40,
                      "assigneeId": 70,
                      "targetWaypointIndex": 0,
                      "traversalDirection": 1,
                      "active": false
                    }
                  }],
                  "selectedRoutes": []
                }
                """);

        FarAndWideSavedData restored = RouteCodecs.SAVED_DATA.parse(JsonOps.INSTANCE, saved).getOrThrow();

        assertEquals(41, restored.getNextRouteId());
        assertEquals(71, restored.getNextAssigneeId());
        assertEquals(41, restored.createRoute().getId());
        assertEquals(71, restored.allocateAssigneeId());
    }

    @Test
    void deletingRouteRemovesAssignmentsAndSelections() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = data.createRoute();
        data.addWaypoint(route.getId(), new Waypoint(Vec3.ZERO, OVERWORLD));
        int assigneeId = data.allocateAssigneeId();
        assertNotNull(data.assignRoute(route.getId(), assigneeId, Vec3.ZERO, OVERWORLD));
        assertTrue(data.setSelectedRouteId(assigneeId, route.getId()));

        assertTrue(data.deleteRoute(route.getId()));

        assertNull(data.getRoute(route.getId()));
        assertNull(data.getAssignment(assigneeId));
        assertEquals(0, data.getSelectedRouteId(assigneeId));

        FarAndWideSavedData restored = roundTrip(data);
        assertNull(restored.getRoute(route.getId()));
        assertNull(restored.getAssignment(assigneeId));
        assertEquals(0, restored.getSelectedRouteId(assigneeId));
    }

    @Test
    void malformedReferencesAndTraversalStateAreRepaired() {
        JsonElement saved = JsonParser.parseString("""
                {
                  "dataVersion": 1,
                  "nextRouteId": 1,
                  "nextAssigneeId": 1,
                  "routes": [
                    {"id": 2, "name": "Valid", "traversalType": "loop", "waypoints": [
                      {"x": 0, "y": 64, "z": 0, "dimension": "minecraft:overworld"}
                    ]},
                    {"id": 2, "name": "Duplicate", "traversalType": "one_way", "waypoints": []},
                    {"id": -1, "name": "Invalid", "traversalType": "one_way", "waypoints": []}
                  ],
                  "assignments": [
                    {"assigneeId": 5, "assignment": {"routeId": 2, "assigneeId": 999,
                      "targetWaypointIndex": 99, "traversalDirection": 0, "active": true}},
                    {"assigneeId": 6, "assignment": {"routeId": 404, "assigneeId": 6,
                      "targetWaypointIndex": 0, "traversalDirection": 1, "active": true}}
                  ],
                  "selectedRoutes": [
                    {"assigneeId": 7, "routeId": 404},
                    {"assigneeId": 8, "routeId": 2}
                  ]
                }
                """);

        FarAndWideSavedData restored = RouteCodecs.SAVED_DATA.parse(JsonOps.INSTANCE, saved).getOrThrow();

        assertEquals(1, restored.getRoutes().size());
        assertEquals("Valid", restored.getRoute(2).getName());
        RouteAssignment assignment = restored.getAssignment(5);
        assertNotNull(assignment);
        assertEquals(5, assignment.getAssigneeId());
        assertEquals(0, assignment.getTargetWaypointIndex());
        assertEquals(1, assignment.getTraversalDirection());
        assertNull(restored.getAssignment(6));
        assertEquals(0, restored.getSelectedRouteId(7));
        assertEquals(2, restored.getSelectedRouteId(8));
        assertEquals(3, restored.getNextRouteId());
        assertEquals(9, restored.getNextAssigneeId());
    }

    private static FarAndWideSavedData roundTrip(FarAndWideSavedData data) {
        JsonElement encoded = RouteCodecs.SAVED_DATA.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        return RouteCodecs.SAVED_DATA.parse(JsonOps.INSTANCE, encoded).getOrThrow();
    }
}
