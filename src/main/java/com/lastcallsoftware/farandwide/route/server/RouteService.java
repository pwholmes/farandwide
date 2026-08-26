package com.lastcallsoftware.farandwide.route.server;

import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.route.RouteOperationResult;
import com.lastcallsoftware.farandwide.route.TraversalType;
import com.lastcallsoftware.farandwide.route.Waypoint;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideAttachments;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideSavedData;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Authoritative entry point for player-initiated route operations.
 *
 * <p>Network handlers call this class after decoding a request. Methods resolve
 * the correct world data, determine whether the player or ridden vehicle is the
 * assignee, validate the request, and delegate permanent changes to
 * {@code FarAndWideSavedData}. Screens and other client code must never call this
 * class or assume a request succeeded before its server result arrives.
 *
 * <p>Each mutation returns a {@code RouteOperationResult}. Do validation before
 * the first mutation so a rejected request cannot leave partially changed state.
 */
public final class RouteService {
    private static final double WAYPOINT_REMOVE_RADIUS = 5.0;

    private RouteService() {
    }

    public static RouteState getRoutes(ServerPlayer player) {
        FarAndWideSavedData data = data(player);
        int playerId = assigneeId(player, data);
        return new RouteState(data.getRoutes(), data.getSelectedRouteId(playerId));
    }

    public static RouteState getRoutesForBroadcast(ServerPlayer player) {
        // Selections belong to individual persistent player IDs. A broadcast
        // therefore carries no selection; the initiating player receives a
        // second, personalized snapshot through getRoutes(player).
        return new RouteState(data(player).getRoutes(), 0);
    }

    public static AssignmentState getAssignment(ServerPlayer player) {
        FarAndWideSavedData data = data(player);
        Entity assignee = controlledAssignee(player);
        return new AssignmentState(assignee.getId(), data.getAssignment(assigneeId(assignee, data)));
    }

    public static RouteOperationResult createRoute(ServerPlayer player, String name, TraversalType traversalType) {
        if (name == null || name.isBlank()) {
            return RouteOperationResult.EMPTY_NAME;
        }
        FarAndWideSavedData data = data(player);
        Route route = data.createRoute();
        data.renameRoute(route.getId(), name);
        data.setTraversalType(route.getId(), traversalType);
        data.setSelectedRouteId(assigneeId(player, data), route.getId());
        return RouteOperationResult.SUCCESS;
    }

    public static RouteOperationResult updateRoute(
            ServerPlayer player, int routeId, String name, TraversalType traversalType) {
        FarAndWideSavedData data = data(player);
        if (data.getRoute(routeId) == null) {
            return RouteOperationResult.ROUTE_NOT_FOUND;
        }
        if (name == null || name.isBlank()) {
            return RouteOperationResult.EMPTY_NAME;
        }
        data.renameRoute(routeId, name);
        data.setTraversalType(routeId, traversalType);
        return RouteOperationResult.SUCCESS;
    }

    public static RouteOperationResult deleteRoute(ServerPlayer player, int routeId) {
        return data(player).deleteRoute(routeId)
                ? RouteOperationResult.SUCCESS
                : RouteOperationResult.ROUTE_NOT_FOUND;
    }

    public static RouteOperationResult selectRoute(ServerPlayer player, int routeId) {
        FarAndWideSavedData data = data(player);
        if (routeId == 0) {
            data.clearSelectedRouteId(assigneeId(player, data));
            return RouteOperationResult.SUCCESS;
        }
        if (data.getRoute(routeId) == null) {
            return RouteOperationResult.ROUTE_NOT_FOUND;
        }
        data.setSelectedRouteId(assigneeId(player, data), routeId);
        return RouteOperationResult.SUCCESS;
    }

    public static RouteOperationResult addWaypoint(ServerPlayer player, int routeId) {
        return data(player).addWaypoint(routeId, waypointInFrontOf(player))
                ? RouteOperationResult.SUCCESS
                : RouteOperationResult.ROUTE_NOT_FOUND;
    }

    public static RouteOperationResult removeWaypoint(ServerPlayer player, int routeId) {
        FarAndWideSavedData data = data(player);
        if (data.getRoute(routeId) == null) {
            return RouteOperationResult.ROUTE_NOT_FOUND;
        }
        return data.removeNearestWaypoint(routeId, player.position(), dimension(player), WAYPOINT_REMOVE_RADIUS)
                ? RouteOperationResult.SUCCESS
                : RouteOperationResult.NO_WAYPOINT_IN_DIMENSION;
    }

    public static RouteOperationResult toggleWaypoint(ServerPlayer player, int routeId) {
        FarAndWideSavedData data = data(player);
        if (data.getRoute(routeId) == null) {
            return RouteOperationResult.ROUTE_NOT_FOUND;
        }
        data.toggleWaypoint(routeId, player.position(), dimension(player), WAYPOINT_REMOVE_RADIUS,
                waypointInFrontOf(player));
        return RouteOperationResult.SUCCESS;
    }

    public static RouteOperationResult assignRoute(ServerPlayer player, int routeId) {
        FarAndWideSavedData data = data(player);
        Route route = data.getRoute(routeId);
        if (route == null) {
            return RouteOperationResult.ROUTE_NOT_FOUND;
        }
        if (route.getWaypoints().isEmpty()) {
            return RouteOperationResult.NO_WAYPOINTS;
        }
        Entity assignee = controlledAssignee(player);
        RouteAssignment assignment = data.assignRoute(
                routeId, assigneeId(assignee, data), assignee.position(), dimension(assignee));
        return assignment == null
                ? RouteOperationResult.NO_WAYPOINT_IN_DIMENSION
                : RouteOperationResult.SUCCESS;
    }

    public static RouteOperationResult toggleAssignment(ServerPlayer player) {
        FarAndWideSavedData data = data(player);
        int assigneeId = assigneeId(controlledAssignee(player), data);
        RouteAssignment assignment = data.getAssignment(assigneeId);
        if (assignment == null) {
            return RouteOperationResult.NO_ASSIGNMENT;
        }
        data.setAssignmentActive(assigneeId, !assignment.isActive());
        return RouteOperationResult.SUCCESS;
    }

    private static FarAndWideSavedData data(ServerPlayer player) {
        return FarAndWideSavedData.get(player.level().getServer());
    }

    private static int assigneeId(Entity entity, FarAndWideSavedData data) {
        return FarAndWideAttachments.getOrCreateAssigneeId(entity, data);
    }

    private static Entity controlledAssignee(ServerPlayer player) {
        // Route input controls the ridden entity when present. This same rule is
        // used for assignment lookup, mutation, and snapshot delivery.
        return player.getVehicle() == null ? player : player.getVehicle();
    }

    private static net.minecraft.resources.Identifier dimension(Entity entity) {
        return entity.level().dimension().identifier();
    }

    private static Waypoint waypointInFrontOf(ServerPlayer player) {
        // Ignore vertical look direction so looking up or down does not place a
        // waypoint above or below the player's current elevation.
        Vec3 look = player.getLookAngle();
        Vec3 position = player.position().add(
                look.x * Route.OFFSET_DISTANCE,
                0.0,
                look.z * Route.OFFSET_DISTANCE);
        return new Waypoint(position, dimension(player));
    }

    /** Transport-neutral data needed to build a route snapshot. */
    public record RouteState(List<Route> routes, int selectedRouteId) {
    }

    /** Assignment plus the runtime entity ID expected by the receiving client. */
    public record AssignmentState(int entityId, RouteAssignment assignment) {
    }
}
