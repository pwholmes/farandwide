package com.lastcallsoftware.farandwide.route.client;

import static org.junit.jupiter.api.Assertions.*;

import com.lastcallsoftware.farandwide.route.*;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class OrderScreenSupportTest {
    @Test
    void oneWayRoutesCanSupplyOrdersAndAppearInAnyJourneyLeg() {
        Route first = fourStops(1, "First", 0, 10);
        Route second = fourStops(2, "Second", 10, 20);
        Route third = fourStops(3, "Third", 20, 30);
        for (int excluded = 0; excluded < 3; excluded++) {
            java.util.ArrayList<Route> routes = new java.util.ArrayList<>(List.of(first, second, third));
            Route original = routes.get(excluded);
            Route oneWay = new Route(original.id(), original.name(), TraversalType.ONE_WAY, original.waypoints());
            routes.set(excluded, oneWay);
            List<OrderJourney> orderJourneys = OrderScreenSupport.journeys(
                    routes, routes.getFirst(), routes.getFirst().waypoints().getFirst());
            assertEquals(3, orderJourneys.size());
            boolean includesOneWayRoute = false;
            for (OrderJourney journey : orderJourneys) {
                if (journey.legs().stream().anyMatch(leg -> leg.routeId() == oneWay.id())) {
                    includesOneWayRoute = true;
                }
            }
            assertTrue(includesOneWayRoute);
        }
    }

    @Test
    void loopAndReverseRoutesCanBeCombinedInOneJourney() {
        Route first = fourStops(1, "Loop", 0, 10);
        Route linked = fourStops(2, "Reverse", 10, 20);
        Route reverse = new Route(linked.id(), linked.name(), TraversalType.REVERSE, linked.waypoints());
        List<OrderJourney> journeys = OrderScreenSupport.journeys(List.of(first, reverse), first, first.waypoints().getFirst());
        assertEquals(2, journeys.size());
        assertEquals(2, journeys.getLast().legs().getLast().routeId());
    }

    @Test
    void deliveriesIncludeEveryFirstRouteDropoffAndItsContinuations() {
        Waypoint pickup = cargo(11, CargoOperation.LOAD, 0);
        Waypoint firstDropoff = cargo(12, CargoOperation.UNLOAD, 10);
        Waypoint secondDropoff = cargo(13, CargoOperation.UNLOAD, 20);
        Route first = route(1, "Warehouse", pickup, firstDropoff, secondDropoff);
        Route second = route(2, "Harbor", cargo(21, CargoOperation.LOAD, 10), cargo(22, CargoOperation.UNLOAD, 30));
        List<OrderJourney> journeys = OrderScreenSupport.journeys(List.of(first, second), first, pickup);

        assertEquals(List.of(
                journey(new OrderJourney.Leg(1, 11, 12)),
                journey(new OrderJourney.Leg(1, 11, 12), new OrderJourney.Leg(2, 21, 22)),
                journey(new OrderJourney.Leg(1, 11, 13))), journeys);
    }

    @Test
    void equalWaypointOrdinalsAndLegCountsRemainDistinctDeliveryChoices() {
        Route warehouse = fourStops(1, "Warehouse", 0, 10);
        Route harbor = fourStops(2, "Harbor", 10, 20);
        Route village = fourStops(3, "Village", 10, 30);
        List<OrderJourney> journeys = OrderScreenSupport.journeys(List.of(warehouse, harbor, village),
                warehouse, warehouse.getWaypoints().getFirst());

        assertEquals(3, journeys.size());
        assertEquals(4, OrderScreenSupport.ordinal(warehouse, journeys.getFirst().legs().getLast().destinationWaypointId()));
        List<OrderJourney> linked = journeys.stream().filter(journey -> journey.legs().size() == 2).toList();
        assertEquals(List.of(2, 3), linked.stream().map(journey -> journey.legs().getLast().routeId()).toList());
        assertEquals(4, OrderScreenSupport.ordinal(harbor, linked.getFirst().legs().getLast().destinationWaypointId()));
        assertEquals(4, OrderScreenSupport.ordinal(village, linked.getLast().legs().getLast().destinationWaypointId()));
        assertNotEquals(linked.getFirst(), linked.getLast());
    }

    @Test
    void endpointChoicesGroupAlternativeJourneysWithoutLosingThem() {
        OrderJourney direct = journey(new OrderJourney.Leg(1, 11, 14));
        OrderJourney firstPath = journey(new OrderJourney.Leg(1, 11, 12), new OrderJourney.Leg(2, 21, 24));
        OrderJourney secondPath = journey(new OrderJourney.Leg(1, 11, 13), new OrderJourney.Leg(2, 22, 24));
        OrderJourney otherStop = journey(new OrderJourney.Leg(1, 11, 12), new OrderJourney.Leg(2, 21, 23));
        List<OrderJourney> journeys = List.of(direct, firstPath, secondPath, otherStop);

        assertEquals(List.of(1, 2), OrderScreenSupport.destinationRouteIds(journeys));
        assertEquals(List.of(24, 23), OrderScreenSupport.destinationWaypointIds(journeys, 2));
        assertEquals(List.of(firstPath, secondPath), OrderScreenSupport.journeysTo(journeys, 2, 24));
    }

    @Test
    void preferredJourneyUsesTheLongestAvailableItinerary() {
        OrderJourney direct = journey(new OrderJourney.Leg(1, 11, 12));
        OrderJourney linked = journey(new OrderJourney.Leg(1, 11, 12), new OrderJourney.Leg(2, 21, 22));

        assertEquals(linked, OrderScreenSupport.preferredJourney(List.of(direct, linked)));
    }

    @Test
    void linkedDeliveryStopsAtThreeLegsAndAllowsDifferentStationFaces() {
        Route first = fourStops(1, "First", 0, 10);
        Route second = fourStops(2, "Second", 10, 20);
        Route third = fourStops(3, "Third", 20, 30);
        Route fourth = fourStops(4, "Fourth", 30, 40);
        Waypoint wrongFace = new Waypoint(51, Vec3.ZERO, Waypoint.DEFAULT_DIMENSION,
                WaypointAction.cargo(CargoBehavior.unfiltered(CargoOperation.LOAD)
                        .withLoadStation(new CargoStationBinding(new BlockPos(10, 64, 0), Direction.DOWN))));
        Route linkedByBlock = route(5, "Different face", wrongFace, cargo(54, CargoOperation.UNLOAD, 50));
        List<OrderJourney> journeys = OrderScreenSupport.journeys(List.of(first, second, third, fourth, linkedByBlock),
                first, first.getWaypoints().getFirst());

        assertEquals(List.of(1, 2, 3, 2), journeys.stream().map(journey -> journey.legs().size()).toList());
        assertEquals(3, journeys.get(2).legs().getLast().routeId());
        assertEquals(5, journeys.getLast().legs().getLast().routeId());
    }

    private static OrderJourney journey(OrderJourney.Leg... legs) { return new OrderJourney(List.of(legs)); }

    private static Route route(int id, String name, Waypoint... waypoints) {
        return new Route(id, name, TraversalType.LOOP, List.of(waypoints));
    }

    private static Route fourStops(int id, String name, int pickupX, int deliveryX) {
        return route(id, name, cargo(id * 10 + 1, CargoOperation.LOAD, pickupX),
                new Waypoint(id * 10 + 2, Vec3.ZERO, Waypoint.DEFAULT_DIMENSION, WaypointAction.normal()),
                new Waypoint(id * 10 + 3, Vec3.ZERO, Waypoint.DEFAULT_DIMENSION, WaypointAction.normal()),
                cargo(id * 10 + 4, CargoOperation.UNLOAD, deliveryX));
    }

    private static Waypoint cargo(int id, CargoOperation operation, int x) {
        CargoStationBinding station = new CargoStationBinding(new BlockPos(x, 64, 0), Direction.UP);
        CargoBehavior behavior = CargoBehavior.unfiltered(operation);
        behavior = operation == CargoOperation.LOAD ? behavior.withLoadStation(station) : behavior.withUnloadStation(station);
        return new Waypoint(id, new Vec3(x, 64, 0), Waypoint.DEFAULT_DIMENSION, WaypointAction.cargo(behavior));
    }
}
