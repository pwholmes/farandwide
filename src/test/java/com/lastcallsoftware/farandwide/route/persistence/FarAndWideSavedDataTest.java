package com.lastcallsoftware.farandwide.route.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.CargoBehavior;
import com.lastcallsoftware.farandwide.route.CargoFilter;
import com.lastcallsoftware.farandwide.route.CargoOperation;
import com.lastcallsoftware.farandwide.route.CargoStationBinding;
import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.route.TraversalType;
import com.lastcallsoftware.farandwide.route.Waypoint;
import com.lastcallsoftware.farandwide.route.WaypointAction;
import net.minecraft.resources.Identifier;
import org.eclipse.jdt.annotation.NonNull;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class FarAndWideSavedDataTest {
    private static final Identifier OVERWORLD = Identifier.parse("minecraft:overworld");
    private static final Identifier NETHER = Identifier.parse("minecraft:the_nether");

    @Test
    void oldWaypointsReceiveStableIdsAndNormalBehaviorInRouteOrder() {
        JsonElement saved = JsonParser.parseString("""
                {
                  "dataVersion": 1,
                  "routes": [{
                    "id": 4,
                    "name": "Legacy",
                    "traversalType": "one_way",
                    "waypoints": [
                      {"x": 1, "y": 64, "z": 3, "dimension": "minecraft:overworld"},
                      {"x": 9, "y": 70, "z": 2, "dimension": "minecraft:the_nether"}
                    ]
                  }]
                }
                """);

        FarAndWideSavedData restored = RouteCodecs.SAVED_DATA.parse(JsonOps.INSTANCE, saved).getOrThrow();
        List<Waypoint> waypoints = restored.getRoute(4).getWaypoints();

        assertEquals(List.of(1, 2), waypoints.stream().map((@NonNull Waypoint waypoint) -> waypoint.id()).toList());
        assertEquals(List.of(new Vec3(1, 64, 3), new Vec3(9, 70, 2)),
                waypoints.stream().map((@NonNull Waypoint waypoint) -> waypoint.position()).toList());
        assertEquals(List.of(OVERWORLD, NETHER), waypoints.stream().map((@NonNull Waypoint waypoint) -> waypoint.dimension()).toList());
        assertTrue(waypoints.stream().allMatch(waypoint -> waypoint.action() instanceof WaypointAction.Normal));
    }

    @Test
    void newWaypointIdsAreUniqueAcrossRoutesAfterMigration() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route firstRoute = data.createRoute();
        Route secondRoute = data.createRoute();

        assertTrue(data.addWaypoint(firstRoute.getId(), new Waypoint(Vec3.ZERO, OVERWORLD)));
        assertTrue(data.addWaypoint(secondRoute.getId(), new Waypoint(Vec3.ZERO, OVERWORLD)));

        assertEquals(1, data.getRoute(firstRoute.getId()).getWaypoints().getFirst().id());
        assertEquals(2, data.getRoute(secondRoute.getId()).getWaypoints().getFirst().id());
        assertEquals(3, data.getNextWaypointId());
    }

    @Test
    void waypointMutationsUseStableIdInsteadOfMutableIndex() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = data.createRoute();
        data.addWaypoint(route.getId(), new Waypoint(new Vec3(1, 64, 0), OVERWORLD));
        data.addWaypoint(route.getId(), new Waypoint(new Vec3(2, 64, 0), OVERWORLD));
        int firstId = data.getRoute(route.getId()).getWaypoints().get(0).id();
        int secondId = data.getRoute(route.getId()).getWaypoints().get(1).id();
        CargoBehavior cargo = CargoBehavior.unfiltered(CargoOperation.LOAD);

        assertTrue(data.removeWaypointById(route.getId(), firstId));
        assertTrue(data.convertWaypoint(route.getId(), secondId, WaypointAction.cargo(cargo)));

        Waypoint remaining = data.getRoute(route.getId()).getWaypoints().getFirst();
        assertEquals(secondId, remaining.id());
        assertEquals(WaypointAction.cargo(cargo), remaining.action());
    }

    @Test
    void replacementCannotChangeStableWaypointId() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = data.createRoute();
        data.addWaypoint(route.getId(), new Waypoint(Vec3.ZERO, OVERWORLD));
        int waypointId = data.getRoute(route.getId()).getWaypoints().getFirst().id();

        assertTrue(data.replaceWaypoint(route.getId(), waypointId,
                new Waypoint(999, new Vec3(8, 70, 4), NETHER, WaypointAction.normal())));

        Waypoint replaced = data.getWaypoint(route.getId(), waypointId);
        assertNotNull(replaced);
        assertEquals(waypointId, replaced.id());
        assertEquals(new Vec3(8, 70, 4), replaced.position());
    }

    @Test
    void movingWaypointPreservesStableIdsAndAssignmentTarget() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = data.createRoute();
        data.addWaypoint(route.getId(), new Waypoint(new Vec3(1, 64, 0), OVERWORLD));
        data.addWaypoint(route.getId(), new Waypoint(new Vec3(2, 64, 0), OVERWORLD));
        data.addWaypoint(route.getId(), new Waypoint(new Vec3(3, 64, 0), OVERWORLD));
        route = data.getRoute(route.getId());
        int firstId = route.getWaypoints().get(0).id();
        int secondId = route.getWaypoints().get(1).id();
        int thirdId = route.getWaypoints().get(2).id();
        int assigneeId = data.allocateAssigneeId();
        data.assignRoute(route.getId(), assigneeId, new Vec3(2, 64, 0), OVERWORLD);
        data.updateAssignmentProgress(assigneeId, 1, 1);

        Waypoint third = data.getWaypoint(route.getId(), thirdId);
        assertTrue(data.replaceWaypoint(route.getId(), thirdId, third, 1));

        route = data.getRoute(route.getId());
        assertEquals(List.of(firstId, thirdId, secondId),
                route.getWaypoints().stream().map((@NonNull Waypoint waypoint) -> waypoint.id()).toList());
        assertEquals(2, data.getAssignment(assigneeId).getTargetWaypointIndex());
    }

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
        assertTrue(original.setAssignmentActive(assigneeId, true));
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
    void vehicleIdentitySurvivesRoundTripAndIsRemovedWithAssignment() {
        FarAndWideSavedData original = new FarAndWideSavedData();
        Route route = original.createRoute();
        original.addWaypoint(route.getId(), new Waypoint(Vec3.ZERO, OVERWORLD));
        int assigneeId = original.allocateAssigneeId();
        original.assignRoute(route.getId(), assigneeId, Vec3.ZERO, OVERWORLD);
        UUID vehicleUuid = UUID.fromString("fb3fb68f-b3c4-4d8f-8d70-b51ce76e9abc");

        assertTrue(original.associateVehicle(vehicleUuid, assigneeId));

        FarAndWideSavedData restored = roundTrip(original);
        assertEquals(assigneeId, restored.getVehicleAssigneeId(vehicleUuid));
        assertTrue(restored.removeAssignment(assigneeId));
        assertEquals(0, restored.getVehicleAssigneeId(vehicleUuid));
        assertEquals(0, roundTrip(restored).getVehicleAssigneeId(vehicleUuid));
    }

    @Test
    void vehicleLocationSurvivesRoundTripAndIsRemovedWithAssignment() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = data.createRoute();
        data.addWaypoint(route.getId(), new Waypoint(Vec3.ZERO, OVERWORLD));
        int assigneeId = data.allocateAssigneeId();
        data.assignRoute(route.getId(), assigneeId, Vec3.ZERO, OVERWORLD);
        UUID vehicleUuid = UUID.fromString("1b981ca8-c082-43aa-8812-53758f520ceb");
        BlockPos position = new BlockPos(124, 64, -38);
        data.registerVehicle(vehicleUuid, assigneeId, "oak_boat");

        assertTrue(data.updateVehicleLocation(vehicleUuid, OVERWORLD, position));
        assertFalse(data.updateVehicleLocation(vehicleUuid, OVERWORLD, position));

        FarAndWideSavedData restored = roundTrip(data);
        assertEquals(new FarAndWideSavedData.VehicleLocation(OVERWORLD, position),
                restored.getVehicleLocation(vehicleUuid).orElseThrow());

        assertTrue(restored.removeAssignment(assigneeId));
        assertTrue(restored.getVehicleLocation(vehicleUuid).isEmpty());
        assertTrue(roundTrip(restored).getVehicleLocation(vehicleUuid).isEmpty());
    }

    @Test
    void oneWayRestartAnchorSurvivesRoundTrip() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = data.createRoute();
        data.addWaypoint(route.getId(), new Waypoint(Vec3.ZERO, OVERWORLD));
        data.addWaypoint(route.getId(), new Waypoint(new Vec3(10, 0, 0), OVERWORLD));
        int assigneeId = data.allocateAssigneeId();
        data.assignRoute(route.getId(), assigneeId, Vec3.ZERO, OVERWORLD);
        data.setAssignmentActive(assigneeId, true);

        assertTrue(data.stopAssignmentAtWaypoint(assigneeId, 1, -1));

        RouteAssignment restored = roundTrip(data).getAssignment(assigneeId);
        assertFalse(restored.isActive());
        assertEquals(1, restored.getTargetWaypointIndex());
        assertEquals(-1, restored.getTraversalDirection());
        assertTrue(restored.isRestartAnchor());
    }

    @Test
    void friendlyVehicleNamesAreStableGlobalAndPersisted() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = data.createRoute();
        data.addWaypoint(route.getId(), new Waypoint(Vec3.ZERO, OVERWORLD));
        int boatOneId = data.allocateAssigneeId();
        int boatTwoId = data.allocateAssigneeId();
        int horseOneId = data.allocateAssigneeId();
        data.assignRoute(route.getId(), boatOneId, Vec3.ZERO, OVERWORLD);
        data.assignRoute(route.getId(), boatTwoId, Vec3.ZERO, OVERWORLD);
        data.assignRoute(route.getId(), horseOneId, Vec3.ZERO, OVERWORLD);
        UUID boatOne = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID boatTwo = UUID.fromString("10000000-0000-0000-0000-000000000002");
        UUID horseOne = UUID.fromString("10000000-0000-0000-0000-000000000003");

        assertTrue(data.registerVehicle(boatOne, boatOneId, "oak_boat"));
        assertTrue(data.registerVehicle(boatTwo, boatTwoId, "spruce_boat"));
        assertTrue(data.registerVehicle(horseOne, horseOneId, "horse"));
        assertEquals(List.of("Boat 1", "Boat 2", "Horse 1"), data.getVehicleRouteAssignments().stream()
                .map(assignment -> assignment.displayName()).toList());

        FarAndWideSavedData restored = roundTrip(data);
        assertEquals(List.of("Boat 1", "Boat 2", "Horse 1"), restored.getVehicleRouteAssignments().stream()
                .map(assignment -> assignment.displayName()).toList());

        assertTrue(restored.removeAssignment(boatOneId));
        restored.assignRoute(route.getId(), boatOneId, Vec3.ZERO, OVERWORLD);
        assertTrue(restored.registerVehicle(boatOne, boatOneId, "boat"));
        assertEquals("Boat 1", restored.getVehicleRouteAssignments().stream()
                .filter(assignment -> assignment.assigneeId() == boatOneId)
                .findFirst().orElseThrow().displayName());
    }

    @Test
    void chestBoatNamesIgnoreTheWoodVariant() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = data.createRoute();
        data.addWaypoint(route.getId(), new Waypoint(Vec3.ZERO, OVERWORLD));
        int spruceId = data.allocateAssigneeId();
        int oakId = data.allocateAssigneeId();
        data.assignRoute(route.getId(), spruceId, Vec3.ZERO, OVERWORLD);
        data.assignRoute(route.getId(), oakId, Vec3.ZERO, OVERWORLD);

        assertTrue(data.registerVehicle(
                UUID.fromString("10000000-0000-0000-0000-000000000004"),
                spruceId,
                "spruce_boat_with_chest"));
        assertTrue(data.registerVehicle(
                UUID.fromString("10000000-0000-0000-0000-000000000005"),
                oakId,
                "oak_boat_with_chest"));

        assertEquals(List.of("Chest Boat 1", "Chest Boat 2"), data.getVehicleRouteAssignments().stream()
                .map(assignment -> assignment.displayName()).toList());

        FarAndWideSavedData restored = roundTrip(data);
        assertEquals(List.of("Chest Boat 1", "Chest Boat 2"), restored.getVehicleRouteAssignments().stream()
                .map(assignment -> assignment.displayName()).toList());
    }

    @Test
    void customVehicleNameOverridesAndCanRestoreTheGeneratedFallback() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = data.createRoute();
        data.addWaypoint(route.getId(), new Waypoint(Vec3.ZERO, OVERWORLD));
        int assigneeId = data.allocateAssigneeId();
        data.assignRoute(route.getId(), assigneeId, Vec3.ZERO, OVERWORLD);
        UUID boat = UUID.fromString("20000000-0000-0000-0000-000000000001");
        data.registerVehicle(boat, assigneeId, "oak_boat");

        assertTrue(data.updateVehicleCustomName(boat, "Sea Breeze"));
        assertEquals("Sea Breeze", data.getVehicleRouteAssignments().getFirst().displayName());

        FarAndWideSavedData restored = roundTrip(data);
        assertEquals("Sea Breeze", restored.getVehicleRouteAssignments().getFirst().displayName());
        assertTrue(restored.updateVehicleCustomName(boat, null));
        assertEquals("Boat 1", restored.getVehicleRouteAssignments().getFirst().displayName());
    }

    @Test
    void activatingLegacyOneWayEndpointCreatesRestartAnchor() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = data.createRoute();
        data.setTraversalType(route.getId(), TraversalType.ONE_WAY);
        data.addWaypoint(route.getId(), new Waypoint(Vec3.ZERO, OVERWORLD));
        data.addWaypoint(route.getId(), new Waypoint(new Vec3(10, 0, 0), OVERWORLD));
        int assigneeId = data.allocateAssigneeId();
        data.assignRoute(route.getId(), assigneeId, Vec3.ZERO, OVERWORLD);
        data.updateAssignmentProgress(assigneeId, 1, 1);

        assertTrue(data.setAssignmentActive(assigneeId, true));

        RouteAssignment result = data.getAssignment(assigneeId);
        assertTrue(result.isActive());
        assertTrue(result.isRestartAnchor());
        assertEquals(1, result.getTargetWaypointIndex());
        assertEquals(1, result.getTraversalDirection());
    }

    @Test
    void updatingOneVehicleWaypointDoesNotChangeAnother() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = data.createRoute();
        data.addWaypoint(route.getId(), new Waypoint(Vec3.ZERO, OVERWORLD));
        data.addWaypoint(route.getId(), new Waypoint(new Vec3(10, 0, 0), OVERWORLD));
        int first = data.allocateAssigneeId();
        int second = data.allocateAssigneeId();
        data.assignRoute(route.getId(), first, Vec3.ZERO, OVERWORLD);
        data.assignRoute(route.getId(), second, Vec3.ZERO, OVERWORLD);

        assertTrue(data.updateAssignmentProgress(first, 1, 1));

        assertEquals(1, data.getAssignment(first).getTargetWaypointIndex());
        assertEquals(0, data.getAssignment(second).getTargetWaypointIndex());
    }

    @Test
    void vehicleIdentityRequiresAnExistingAssignment() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        UUID vehicleUuid = UUID.fromString("6e0c1347-aa49-4314-8711-ff4b2f94910b");

        assertFalse(data.associateVehicle(vehicleUuid, 42));
        assertEquals(0, data.getVehicleAssigneeId(vehicleUuid));
    }

    @Test
    void deletingRouteRemovesVehicleIdentityWithItsAssignment() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = data.createRoute();
        data.addWaypoint(route.getId(), new Waypoint(Vec3.ZERO, OVERWORLD));
        int assigneeId = data.allocateAssigneeId();
        data.assignRoute(route.getId(), assigneeId, Vec3.ZERO, OVERWORLD);
        UUID vehicleUuid = UUID.fromString("6153653d-300b-462c-85cb-e046d620bd3a");
        data.associateVehicle(vehicleUuid, assigneeId);

        assertTrue(data.deleteRoute(route.getId()));

        assertEquals(0, data.getVehicleAssigneeId(vehicleUuid));
        assertNull(data.getAssignment(assigneeId));
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
    void cargoWaypointFieldsSurviveDiskRoundTrip() {
        FarAndWideSavedData original = new FarAndWideSavedData();
        Route route = original.createRoute();
        CargoBehavior behavior = new CargoBehavior(
                CargoOperation.UNLOAD_THEN_LOAD,
                CargoFilter.allowList(List.of(
                        Identifier.parse("minecraft:coal"),
                        Identifier.parse("missing_mod:charcoal_briquette"))),
                CargoFilter.allowList(List.of(
                        Identifier.parse("minecraft:iron_ingot"),
                        Identifier.parse("minecraft:copper_ingot"))),
                Optional.of(new CargoStationBinding(new BlockPos(5, 63, 9), Direction.UP)),
                Optional.of(new CargoStationBinding(new BlockPos(6, 63, 9), Direction.EAST)));
        assertTrue(original.addWaypoint(route.getId(), new Waypoint(
                0, new Vec3(5, 64, 9), NETHER, WaypointAction.cargo(behavior))));
        Waypoint persisted = original.getRoute(route.getId()).getWaypoints().getFirst();

        FarAndWideSavedData restored = roundTrip(original);

        assertEquals(persisted, restored.getRoute(route.getId()).getWaypoints().getFirst());
        assertEquals(original.getNextWaypointId(), restored.getNextWaypointId());
    }

    @Test
    void legacySingleStationBindingAppliesToBothCargoDirections() {
        JsonElement saved = JsonParser.parseString("""
                {
                  "routes": [{
                    "id": 1,
                    "name": "Legacy Cargo",
                    "traversalType": "one_way",
                    "waypoints": [{
                      "id": 1,
                      "x": 2.5, "y": 64.5, "z": 4.5,
                      "dimension": "minecraft:overworld",
                      "cargo": {
                        "operation": "unload_then_load",
                        "loadFilter": {"mode": "all"},
                        "unloadFilter": {"mode": "all"},
                        "station": {"x": 2, "y": 63, "z": 4, "side": "north"}
                      }
                    }]
                  }]
                }
                """);

        FarAndWideSavedData restored = RouteCodecs.SAVED_DATA.parse(JsonOps.INSTANCE, saved).getOrThrow();
        WaypointAction.Cargo cargo = (WaypointAction.Cargo) restored.getRoute(1).getWaypoints().getFirst().action();
        CargoStationBinding expected = new CargoStationBinding(new BlockPos(2, 63, 4), Direction.NORTH);

        assertEquals(expected, cargo.behavior().loadStation().orElseThrow());
        assertEquals(expected, cargo.behavior().unloadStation().orElseThrow());
    }

    @Test
    void clearingSelectedRouteRemovesItsPersistedSelection() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = data.createRoute();
        int assigneeId = data.allocateAssigneeId();
        assertTrue(data.setSelectedRouteId(assigneeId, route.getId()));

        assertTrue(data.clearSelectedRouteId(assigneeId));
        assertEquals(0, data.getSelectedRouteId(assigneeId));
        assertFalse(data.clearSelectedRouteId(assigneeId));

        assertEquals(0, roundTrip(data).getSelectedRouteId(assigneeId));
    }

    @Test
    void routeActivationChangesEveryAssignmentUsingThatRouteOnly() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = data.createRoute();
        Route otherRoute = data.createRoute();
        data.addWaypoint(route.getId(), new Waypoint(Vec3.ZERO, OVERWORLD));
        data.addWaypoint(otherRoute.getId(), new Waypoint(Vec3.ZERO, OVERWORLD));
        int first = data.allocateAssigneeId();
        int second = data.allocateAssigneeId();
        int unrelated = data.allocateAssigneeId();
        data.assignRoute(route.getId(), first, Vec3.ZERO, OVERWORLD);
        data.assignRoute(route.getId(), second, Vec3.ZERO, OVERWORLD);
        data.assignRoute(otherRoute.getId(), unrelated, Vec3.ZERO, OVERWORLD);
        data.setAssignmentActive(first, true);
        data.setAssignmentActive(second, true);
        data.setAssignmentActive(unrelated, true);

        assertTrue(data.setRouteAssignmentsActive(route.getId(), false));

        assertFalse(data.getAssignment(first).isActive());
        assertFalse(data.getAssignment(second).isActive());
        assertTrue(data.getAssignment(unrelated).isActive());
    }

    @Test
    void individualActivationDoesNotChangeOtherAssignmentsOnTheRoute() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = data.createRoute();
        data.addWaypoint(route.getId(), new Waypoint(Vec3.ZERO, OVERWORLD));
        int first = data.allocateAssigneeId();
        int second = data.allocateAssigneeId();
        data.assignRoute(route.getId(), first, Vec3.ZERO, OVERWORLD);
        data.assignRoute(route.getId(), second, Vec3.ZERO, OVERWORLD);

        assertTrue(data.setAssignmentActive(first, true));

        assertTrue(data.getAssignment(first).isActive());
        assertFalse(data.getAssignment(second).isActive());

        assertTrue(data.setRouteAssignmentsActive(route.getId(), true));
        assertTrue(data.getAssignment(first).isActive());
        assertTrue(data.getAssignment(second).isActive());
    }

    @Test
    void transferringAssignmentPreservesTraversalStateAndRemovesSource() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = data.createRoute();
        data.addWaypoint(route.getId(), new Waypoint(Vec3.ZERO, OVERWORLD));
        int playerId = data.allocateAssigneeId();
        int vehicleId = data.allocateAssigneeId();
        data.assignRoute(route.getId(), playerId, Vec3.ZERO, OVERWORLD);
        data.setAssignmentActive(playerId, true);
        data.setAssignmentTraversalTypeOverride(playerId, TraversalType.REVERSE);

        assertTrue(data.transferAssignment(playerId, vehicleId));

        assertNull(data.getAssignment(playerId));
        RouteAssignment transferred = data.getAssignment(vehicleId);
        assertNotNull(transferred);
        assertEquals(vehicleId, transferred.getAssigneeId());
        assertEquals(route.getId(), transferred.getRouteId());
        assertEquals(TraversalType.REVERSE, transferred.getTraversalTypeOverride());
        assertTrue(transferred.isActive());
    }

    @Test
    void firstAssignmentIsInactiveAndLaterAssignmentsInheritRouteActivity() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = data.createRoute();
        data.addWaypoint(route.getId(), new Waypoint(Vec3.ZERO, OVERWORLD));
        int first = data.allocateAssigneeId();
        int second = data.allocateAssigneeId();

        RouteAssignment firstAssignment = data.assignRoute(route.getId(), first, Vec3.ZERO, OVERWORLD);
        assertFalse(firstAssignment.isActive());
        data.setAssignmentActive(first, true);

        RouteAssignment secondAssignment = data.assignRoute(route.getId(), second, Vec3.ZERO, OVERWORLD);
        assertTrue(secondAssignment.isActive());
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
