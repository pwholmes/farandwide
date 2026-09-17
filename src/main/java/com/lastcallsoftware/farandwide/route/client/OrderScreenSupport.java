package com.lastcallsoftware.farandwide.route.client;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.route.*;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/** Shared labels and endpoint choices for the two order screens. */
@NonNullByDefault
final class OrderScreenSupport {
    private OrderScreenSupport() {}

    static List<Route> eligibleRoutes() {
        return RouteManager.getRoutes().stream().filter(route -> origins(route).stream()
                .anyMatch(origin -> !destinations(route, origin).isEmpty())).toList();
    }

    static List<Waypoint> origins(@Nullable Route route) {
        return route == null ? List.of() : route.getWaypoints().stream()
                .filter(waypoint -> waypoint.action() instanceof WaypointAction.Cargo cargo
                        && cargo.behavior().operation() != CargoOperation.UNLOAD
                        && cargo.behavior().loadStation().isPresent() && !cargo.behavior().sourceInventories().isEmpty()).toList();
    }

    static List<Waypoint> destinations(@Nullable Route route, @Nullable Waypoint origin) {
        return route == null || origin == null ? List.of() : route.getWaypoints().stream()
                .filter(waypoint -> waypoint.id() != origin.id() && waypoint.dimension().equals(origin.dimension())
                        && waypoint.action() instanceof WaypointAction.Cargo cargo
                        && cargo.behavior().operation() != CargoOperation.LOAD && cargo.behavior().unloadStation().isPresent()).toList();
    }

    /** Returns every direct delivery and station-linked continuation from the selected pickup. */
    static List<OrderJourney> journeys(List<Route> routes, @Nullable Route route, @Nullable Waypoint origin) {
        if (route == null || origin == null) return List.of();
        List<OrderJourney> journeys = new java.util.ArrayList<>();
        for (Waypoint destination : destinations(route, origin)) {
            OrderJourney.Leg first = new OrderJourney.Leg(route.getId(), origin.id(), destination.id());
            var unload = ((WaypointAction.Cargo) destination.action()).behavior().unloadStation().orElseThrow();
            extend(routes, journeys, List.of(first), unload, destination.dimension());
        }
        return List.copyOf(journeys);
    }

    static int destinationRouteId(OrderJourney journey) {
        return journey.legs().getLast().routeId();
    }

    static int destinationWaypointId(OrderJourney journey) {
        return journey.legs().getLast().destinationWaypointId();
    }

    static List<Integer> destinationRouteIds(List<OrderJourney> journeys) {
        return journeys.stream().map(OrderScreenSupport::destinationRouteId).distinct().toList();
    }

    static List<Integer> destinationWaypointIds(List<OrderJourney> journeys, int routeId) {
        return journeys.stream().filter(journey -> destinationRouteId(journey) == routeId)
                .map(OrderScreenSupport::destinationWaypointId).distinct().toList();
    }

    static List<OrderJourney> journeysTo(List<OrderJourney> journeys, int routeId, int waypointId) {
        return journeys.stream().filter(journey -> destinationRouteId(journey) == routeId
                && destinationWaypointId(journey) == waypointId).toList();
    }

    /** Prefers a linked continuation over a direct delivery when choosing an initial itinerary. */
    static @Nullable OrderJourney preferredJourney(List<OrderJourney> journeys) {
        return journeys.stream().max(Comparator.comparingInt(journey -> journey.legs().size())).orElse(null);
    }

    private static void extend(List<Route> routes, List<OrderJourney> result, List<OrderJourney.Leg> prefix,
            CargoStationBinding handoff, Identifier dimension) {
        result.add(new OrderJourney(prefix));
        if (prefix.size() == Constants.Orders.MAX_LEGS) return;
        for (Route route : routes) for (Waypoint origin : route.getWaypoints()) {
            @Nullable CargoStationBinding loadStation = origin.action() instanceof WaypointAction.Cargo cargo
                    ? cargo.behavior().loadStation().orElse(null) : null;
            if (!(origin.action() instanceof WaypointAction.Cargo cargo) || !origin.dimension().equals(dimension)
                    || cargo.behavior().operation() == CargoOperation.UNLOAD
                    || loadStation == null || !handoff.position().equals(loadStation.position())) continue;
            for (Waypoint destination : destinations(route, origin)) {
                OrderJourney.Leg next = new OrderJourney.Leg(route.getId(), origin.id(), destination.id());
                if (prefix.contains(next)) continue;
                List<OrderJourney.Leg> extended = new java.util.ArrayList<>(prefix);
                extended.add(next);
                extend(routes, result, extended, ((WaypointAction.Cargo) destination.action()).behavior().unloadStation().orElseThrow(), dimension);
            }
        }
    }

    static int ordinal(@Nullable Route route, int waypointId) {
        if (route != null) {
            for (int index = 0; index < route.getWaypoints().size(); index++) {
                if (route.getWaypoints().get(index).id() == waypointId) return index + 1;
            }
        }
        return 0;
    }

    static Component itemName(Identifier id) {
        var item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        return item == null ? Component.literal(id.toString()) : item.getDefaultInstance().getHoverName();
    }

    static Component itemName(ItemResource resource) { return resource.getHoverName(); }
}
