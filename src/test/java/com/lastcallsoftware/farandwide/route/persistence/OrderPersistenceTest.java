package com.lastcallsoftware.farandwide.route.persistence;

import com.lastcallsoftware.farandwide.route.*;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderPersistenceTest {
    private static final Identifier GOLD = Identifier.parse("minecraft:gold_ingot");
    private static final Identifier REDSTONE = Identifier.parse("minecraft:redstone");
    private static final CargoStationBinding DESTINATION = new CargoStationBinding(new BlockPos(30, 0, 0), Direction.UP);
    private static final CargoStationBinding SOURCE = new CargoStationBinding(new BlockPos(2, 0, 0), Direction.NORTH);

    @SuppressWarnings("deprecation")
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (var item : List.of(Items.AIR, Items.GOLD_INGOT, Items.REDSTONE)) {
            item.builtInRegistryHolder().bindComponents(DataComponents.COMMON_ITEM_COMPONENTS);
        }
    }

    @Test void orderProgressAndSourceLinksSurviveReload() {
        Fixture fixture = fixture();
        fixture.data.creditOrders(fixture.order.routeId(), fixture.order.destinationWaypointId(), DESTINATION, GOLD, 2);
        fixture.data.setOrderActivationResult(fixture.order.id(), RouteOperationResult.NO_ASSIGNMENT);
        FarAndWideSavedData restored = roundTrip(fixture.data);
        assertEquals(fixture.data.getOrders(), restored.getOrders());
        assertEquals(fixture.data.getRoutes(), restored.getRoutes());
        assertFalse(restored.getOrder(fixture.order.id()).delivered());
        restored.creditOrders(fixture.order.routeId(), fixture.order.destinationWaypointId(), DESTINATION, GOLD, 2);
        restored.creditOrders(fixture.order.routeId(), fixture.order.destinationWaypointId(), DESTINATION, REDSTONE, 4);
        assertTrue(roundTrip(restored).getOrder(fixture.order.id()).delivered());
    }

    @Test void oldSavesDefaultToNoOrdersOrSourceLinks() {
        Fixture fixture = fixture();
        var json = RouteCodecs.SAVED_DATA.encodeStart(JsonOps.INSTANCE, fixture.data).getOrThrow().getAsJsonObject();
        json.remove("orders");
        for (var waypoint : json.getAsJsonArray("routes").get(0).getAsJsonObject().getAsJsonArray("waypoints")) {
            waypoint.getAsJsonObject().getAsJsonObject("cargo").remove("sourceInventories");
        }
        var restored = RouteCodecs.SAVED_DATA.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertTrue(restored.getOrders().isEmpty());
        assertEquals(2, restored.getRoute(fixture.order.routeId()).getWaypoints().size());
        var behavior = ((WaypointAction.Cargo) restored.getWaypoint(fixture.order.routeId(), fixture.order.originWaypointId()).action()).behavior();
        assertTrue(behavior.sourceInventories().isEmpty());
        assertTrue(behavior.loadStation().isPresent());
    }

    @Test void modifiedItemComponentsSurviveReload() {
        Fixture fixture = fixture();
        var namedStack = Items.GOLD_INGOT.getDefaultInstance();
        namedStack.set(DataComponents.CUSTOM_NAME, Component.literal("The important one"));
        ItemResource named = ItemResource.of(namedStack);
        CargoOrder modifiedOrder = new CargoOrder(UUID.randomUUID(), fixture.order.playerId(), fixture.order.routeId(),
                fixture.order.originWaypointId(), fixture.order.destinationWaypointId(), DESTINATION,
                List.of(new OrderLine(named, 3, 0)), RouteOperationResult.SUCCESS);
        fixture.data.addOrder(modifiedOrder);

        CargoOrder restored = roundTrip(fixture.data).getOrder(modifiedOrder.id());
        assertEquals(named, restored.lines().getFirst().resource());
        assertEquals("The important one", restored.lines().getFirst().resource().getHoverName().getString());
    }

    @Test void multiLegOrderProgressSurvivesReload() {
        Fixture fixture = fixture();
        OrderLeg first = fixture.order.legs().getFirst().credit(ItemResource.of(Items.GOLD_INGOT), 4)
                .credit(ItemResource.of(Items.REDSTONE), 4);
        OrderLeg finalLeg = fixture.order.legs().getFirst().withActivationResult(RouteOperationResult.NO_ASSIGNMENT);
        CargoOrder journey = new CargoOrder(UUID.randomUUID(), fixture.order.playerId(), List.of(first, finalLeg));
        fixture.data.addOrder(journey);

        assertEquals(journey, roundTrip(fixture.data).getOrder(journey.id()));
    }

    @Test void legacyIdentifierOrderLinesStillLoad() {
        Fixture fixture = fixture();
        var json = RouteCodecs.SAVED_DATA.encodeStart(JsonOps.INSTANCE, fixture.data).getOrThrow().getAsJsonObject();
        var line = json.getAsJsonArray("orders").get(0).getAsJsonObject()
                .getAsJsonArray("legs").get(0).getAsJsonObject().getAsJsonArray("lines").get(0).getAsJsonObject();
        line.remove("resource");
        line.addProperty("item", GOLD.toString());

        var restored = RouteCodecs.SAVED_DATA.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(GOLD, restored.getOrder(fixture.order.id()).lines().getFirst().itemId());
    }

    @Test void legacySingleLegOrderLoadsAsOneLegJourney() {
        Fixture fixture = fixture();
        var json = RouteCodecs.SAVED_DATA.encodeStart(JsonOps.INSTANCE, fixture.data).getOrThrow().getAsJsonObject();
        var order = json.getAsJsonArray("orders").get(0).getAsJsonObject();
        var leg = order.getAsJsonArray("legs").get(0).getAsJsonObject();
        order.remove("legs");
        for (String field : List.of("route", "origin", "destination", "destinationStation", "lines", "activation")) {
            order.add(field, leg.get(field));
        }

        CargoOrder restored = RouteCodecs.SAVED_DATA.parse(JsonOps.INSTANCE, json).getOrThrow().getOrder(fixture.order.id());
        assertEquals(1, restored.legs().size());
        assertEquals(fixture.order, restored);
    }

    @Test void receiptsAreAllocatedOldestFirstAndNeverCountTwice() {
        Fixture fixture = fixture();
        CargoOrder second = copy(fixture.order, UUID.randomUUID(), UUID.randomUUID());
        fixture.data.addOrder(second);
        fixture.data.creditOrders(fixture.order.routeId(), fixture.order.destinationWaypointId(), DESTINATION, GOLD, 6);
        assertEquals(0, fixture.data.getOrder(fixture.order.id()).remaining(GOLD));
        assertEquals(2, fixture.data.getOrder(second.id()).remaining(GOLD));
        assertEquals(4, fixture.data.getOrder(fixture.order.id()).remaining(REDSTONE));
        assertFalse(fixture.data.getOrder(fixture.order.id()).delivered());
    }

    @Test void laterLegReceiptsWaitForThePreviousHandoff() {
        Fixture fixture = fixture();
        Route crossing = fixture.data.createRoute();
        CargoStationBinding finalStation = new CargoStationBinding(new BlockPos(60, 0, 0), Direction.UP);
        fixture.data.addWaypoint(crossing.getId(), new Waypoint(0, new Vec3(30, 0, 0), Waypoint.DEFAULT_DIMENSION,
                WaypointAction.cargo(CargoBehavior.unfiltered(CargoOperation.LOAD).withLoadStation(DESTINATION))));
        fixture.data.addWaypoint(crossing.getId(), new Waypoint(0, new Vec3(60, 0, 0), Waypoint.DEFAULT_DIMENSION,
                WaypointAction.cargo(CargoBehavior.unfiltered(CargoOperation.UNLOAD).withUnloadStation(finalStation))));
        List<Waypoint> crossingWaypoints = fixture.data.getRoute(crossing.getId()).getWaypoints();
        OrderLeg crossingLeg = new OrderLeg(crossing.getId(), crossingWaypoints.getFirst().id(), crossingWaypoints.getLast().id(),
                finalStation, fixture.order.lines(), RouteOperationResult.SUCCESS);
        CargoOrder journey = new CargoOrder(UUID.randomUUID(), fixture.order.playerId(), List.of(
                fixture.order.legs().getFirst(), crossingLeg));
        fixture.data.addOrder(journey);

        assertFalse(fixture.data.creditOrders(crossing.getId(), crossingLeg.destinationWaypointId(), finalStation, GOLD, 4));
        assertTrue(fixture.data.creditOrders(fixture.order.routeId(), fixture.order.destinationWaypointId(), DESTINATION, GOLD, 8));
        assertTrue(fixture.data.creditOrders(crossing.getId(), crossingLeg.destinationWaypointId(), finalStation, GOLD, 4));
        assertEquals(0, fixture.data.getOrder(journey.id()).legs().get(1).remaining(ItemResource.of(Items.GOLD_INGOT)));
    }

    @Test void wrongRouteWaypointOrStationDoesNotReceiveCredit() {
        Fixture fixture = fixture();
        assertFalse(fixture.data.creditOrders(100, fixture.order.destinationWaypointId(), DESTINATION, GOLD, 4));
        assertFalse(fixture.data.creditOrders(fixture.order.routeId(), fixture.order.originWaypointId(), DESTINATION, GOLD, 4));
        assertFalse(fixture.data.creditOrders(fixture.order.routeId(), fixture.order.destinationWaypointId(), SOURCE, GOLD, 4));
        assertEquals(fixture.order, fixture.data.getOrder(fixture.order.id()));
    }

    @Test void completedOrderDoesNotReceiveMoreCredit() {
        Fixture fixture = fixture();
        fixture.data.creditOrders(fixture.order.routeId(), fixture.order.destinationWaypointId(), DESTINATION, GOLD, 100);
        fixture.data.creditOrders(fixture.order.routeId(), fixture.order.destinationWaypointId(), DESTINATION, REDSTONE, 100);
        assertTrue(fixture.data.getOrder(fixture.order.id()).delivered());
        assertFalse(fixture.data.creditOrders(fixture.order.routeId(), fixture.order.destinationWaypointId(), DESTINATION, GOLD, 100));
    }

    @Test void onlyOwnerCanCancelAndAssignmentsAreUnchanged() {
        Fixture fixture = fixture();
        int assignee = fixture.data.allocateAssigneeId();
        fixture.data.assignRoute(fixture.order.routeId(), assignee, Vec3.ZERO, Waypoint.DEFAULT_DIMENSION);
        fixture.data.setAssignmentActive(assignee, true);
        var assignments = fixture.data.getAssignments();
        var routes = fixture.data.getRoutes();
        assertFalse(fixture.data.cancelOrder(fixture.order.id(), UUID.randomUUID()));
        assertTrue(fixture.data.cancelOrder(fixture.order.id(), fixture.order.playerId()));
        assertTrue(fixture.data.getOrders().isEmpty());
        assertEquals(assignments, fixture.data.getAssignments());
        assertEquals(routes, fixture.data.getRoutes());
        assertTrue(roundTrip(fixture.data).getOrders().isEmpty());
    }

    @Test void routeDeletionRemovesTracking() {
        Fixture fixture = fixture();
        fixture.data.deleteRoute(fixture.order.routeId());
        assertTrue(fixture.data.getOrders().isEmpty());
    }

    @Test void waypointDeletionRemovesTrackingAndReorderingPreservesIt() {
        Fixture fixture = fixture();
        fixture.data.invertRoute(fixture.order.routeId());
        assertEquals(fixture.order, fixture.data.getOrder(fixture.order.id()));
        fixture.data.removeWaypointById(fixture.order.routeId(), fixture.order.destinationWaypointId());
        assertTrue(fixture.data.getOrders().isEmpty());
    }

    @Test void changingStationBindingsPreservesSourceLinks() {
        var behavior = new CargoBehavior(CargoOperation.LOAD, CargoFilter.all(), CargoFilter.all(),
                Optional.empty(), Optional.empty(), List.of(SOURCE));
        assertEquals(List.of(SOURCE), behavior.withLoadStation(DESTINATION).withUnloadStation(SOURCE).sourceInventories());
        assertThrows(IllegalArgumentException.class, () -> new CargoBehavior(CargoOperation.LOAD, CargoFilter.all(), CargoFilter.all(),
                Optional.empty(), Optional.empty(), List.of(SOURCE, new CargoStationBinding(SOURCE.position(), Direction.DOWN))));
    }

    private static CargoOrder copy(CargoOrder order, UUID id, UUID owner) {
        return new CargoOrder(id, owner, order.routeId(), order.originWaypointId(), order.destinationWaypointId(),
                order.destinationStation(), order.lines(), order.activationResult());
    }

    private static FarAndWideSavedData roundTrip(FarAndWideSavedData data) {
        return RouteCodecs.SAVED_DATA.parse(JsonOps.INSTANCE,
                RouteCodecs.SAVED_DATA.encodeStart(JsonOps.INSTANCE, data).getOrThrow()).getOrThrow();
    }

    private static Fixture fixture() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = data.createRoute();
        var loading = new CargoBehavior(CargoOperation.LOAD, CargoFilter.all(), CargoFilter.all(),
                Optional.of(new CargoStationBinding(BlockPos.ZERO, Direction.UP)), Optional.empty(), List.of(SOURCE));
        data.addWaypoint(route.getId(), new Waypoint(0, Vec3.ZERO, Waypoint.DEFAULT_DIMENSION, WaypointAction.cargo(loading)));
        data.addWaypoint(route.getId(), new Waypoint(0, new Vec3(30, 0, 0), Waypoint.DEFAULT_DIMENSION,
                WaypointAction.cargo(CargoBehavior.unfiltered(CargoOperation.UNLOAD).withUnloadStation(DESTINATION))));
        List<Waypoint> waypoints = data.getRoute(route.getId()).getWaypoints();
        CargoOrder order = new CargoOrder(UUID.randomUUID(), UUID.randomUUID(), route.getId(), waypoints.getFirst().id(),
                waypoints.getLast().id(), DESTINATION, List.of(new OrderLine(GOLD, 4, 0), new OrderLine(REDSTONE, 4, 0)),
                RouteOperationResult.SUCCESS);
        data.addOrder(order);
        return new Fixture(data, order);
    }

    private record Fixture(FarAndWideSavedData data, CargoOrder order) {}
}
