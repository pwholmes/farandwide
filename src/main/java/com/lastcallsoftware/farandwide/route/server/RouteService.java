package com.lastcallsoftware.farandwide.route.server;

import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.route.CargoBehavior;
import com.lastcallsoftware.farandwide.route.RouteOperationResult;
import com.lastcallsoftware.farandwide.route.TraversalType;
import com.lastcallsoftware.farandwide.route.Waypoint;
import com.lastcallsoftware.farandwide.route.WaypointAction;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideAttachments;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideSavedData;
import com.lastcallsoftware.farandwide.vehicle.server.ServerVehicleController;
import com.lastcallsoftware.farandwide.vehicle.server.VehicleChunkLoadingManager;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.eclipse.jdt.annotation.NonNull;

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

    public static AssignmentState getAssignment(ServerPlayer player, Entity assignee) {
        FarAndWideSavedData data = data(player);
        return new AssignmentState(assignee.getId(), data.getAssignment(assigneeId(assignee, data)));
    }

    public static int getSelectedRouteId(ServerPlayer player) {
        FarAndWideSavedData data = data(player);
        return data.getSelectedRouteId(assigneeId(player, data));
    }

    public static List<AssignmentState> getLoadedAssignmentsForRoute(ServerPlayer player, int routeId) {
        FarAndWideSavedData data = data(player);
        List<AssignmentState> states = new java.util.ArrayList<>();
        for (net.minecraft.server.level.ServerLevel level : player.level().getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!entity.hasData(FarAndWideAttachments.ASSIGNEE_ID.get())) {
                    continue;
                }
                RouteAssignment assignment = data.getAssignment(
                        entity.getData(FarAndWideAttachments.ASSIGNEE_ID.get()));
                if (assignment != null && assignment.getRouteId() == routeId) {
                    states.add(new AssignmentState(entity.getId(), assignment));
                }
            }
        }
        return states;
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
        FarAndWideSavedData data = data(player);
        return deleteRoute(data, routeId, () -> stopLoadedAssignees(player, data, routeId))
                ? RouteOperationResult.SUCCESS
                : RouteOperationResult.ROUTE_NOT_FOUND;
    }

    /**
     * Stops loaded assignees while their route assignment can still be resolved,
     * then removes the route and its persisted references.
     */
    static boolean deleteRoute(FarAndWideSavedData data, int routeId, Runnable stopAssignees) {
        if (data.getRoute(routeId) == null) {
            return false;
        }
        stopAssignees.run();
        return data.deleteRoute(routeId);
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
        FarAndWideSavedData data = data(player);
        RouteOperationResult validation = validateWaypointMutation(player, data, routeId);
        if (validation != RouteOperationResult.SUCCESS) {
            return validation;
        }
        return data.addWaypoint(routeId, waypointInFrontOf(player))
                ? RouteOperationResult.SUCCESS
                : RouteOperationResult.ROUTE_NOT_FOUND;
    }

    public static RouteOperationResult assignRoute(ServerPlayer player, int routeId) {
        FarAndWideSavedData data = data(player);
        Entity assignee = controlledAssignee(player);
        int assigneeId = assigneeId(assignee, data);
        if (unassignRoute(data, assigneeId, () -> {
            ServerVehicleController.stop(assignee);
            VehicleChunkLoadingManager.release(assignee);
        })) {
            return RouteOperationResult.SUCCESS;
        }
        Route route = data.getRoute(routeId);
        if (route == null) {
            return RouteOperationResult.ROUTE_NOT_FOUND;
        }
        if (route.getWaypoints().isEmpty()) {
            return RouteOperationResult.NO_WAYPOINTS;
        }
        if (data.getSelectedRouteId(assigneeId(player, data)) != routeId) {
            return RouteOperationResult.ROUTE_NOT_FOUND;
        }
        RouteAssignment assignment = data.assignRoute(
                routeId, assigneeId, assignee.position(), dimension(assignee));
        if (assignment != null && assignment.isActive()) {
            if (!VehicleChunkLoadingManager.update(assignee, assigneeId)) {
                data.setAssignmentActive(assigneeId, false);
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        RouteOperationResult.CHUNK_LOADING_LIMIT.translationKey()));
            }
        }
        return assignment == null
                ? RouteOperationResult.NO_WAYPOINT_IN_DIMENSION
                : RouteOperationResult.SUCCESS;
    }

    /** Removes an existing assignment, stopping it first only when it is navigating. */
    static boolean unassignRoute(FarAndWideSavedData data, int assigneeId, Runnable stopAssignee) {
        RouteAssignment assignment = data.getAssignment(assigneeId);
        if (assignment == null) {
            return false;
        }
        if (assignment.isActive()) {
            stopAssignee.run();
        }
        return data.removeAssignment(assigneeId);
    }

    public static RouteOperationResult toggleAssignment(ServerPlayer player) {
        FarAndWideSavedData data = data(player);
        int routeId = data.getSelectedRouteId(assigneeId(player, data));
        if (routeId == 0 || data.getRoute(routeId) == null) {
            return RouteOperationResult.NO_ASSIGNMENT;
        }

        List<RouteAssignment> assignments = data.getAssignments().stream()
                .filter(assignment -> assignment.getRouteId() == routeId)
                .toList();
        if (assignments.isEmpty()) {
            return RouteOperationResult.NO_ASSIGNMENT;
        }
        boolean active = assignments.stream().noneMatch((@NonNull RouteAssignment assignment) -> assignment.isActive());
        data.setRouteAssignmentsActive(routeId, active);
        if (!active) {
            stopLoadedAssignees(player, data, routeId);
        }
        return RouteOperationResult.SUCCESS;
    }

    public static RouteOperationResult createWaypoint(ServerPlayer player, int routeId, Vec3 position,
            net.minecraft.resources.Identifier dimension, WaypointAction action, double arrivalRadius) {
        FarAndWideSavedData data = data(player);
        RouteOperationResult validation = validateWaypointMutation(player, data, routeId);
        if (validation != RouteOperationResult.SUCCESS) {
            return validation;
        }
        RouteOperationResult requestValidation = validateWaypointRequest(player, position, dimension, action, arrivalRadius,
                data.getRoute(routeId), 0);
        if (requestValidation != RouteOperationResult.SUCCESS) {
            return requestValidation;
        }
        return data.addWaypoint(routeId, new Waypoint(0, position, dimension, action, arrivalRadius))
                ? RouteOperationResult.SUCCESS
                : RouteOperationResult.ROUTE_NOT_FOUND;
    }

    public static RouteOperationResult replaceWaypoint(ServerPlayer player, int routeId, int waypointId,
            Vec3 position, net.minecraft.resources.Identifier dimension, WaypointAction action, int targetPosition,
            double arrivalRadius) {
        FarAndWideSavedData data = data(player);
        RouteOperationResult validation = validateWaypointMutation(player, data, routeId);
        if (validation != RouteOperationResult.SUCCESS) {
            return validation;
        }
        Route route = data.getRoute(routeId);
        if (data.getWaypoint(routeId, waypointId) == null) {
            return RouteOperationResult.WAYPOINT_NOT_FOUND;
        }
        if (route == null || targetPosition < 0 || targetPosition >= route.getWaypoints().size()) {
            return RouteOperationResult.INVALID_WAYPOINT;
        }
        RouteOperationResult requestValidation = validateWaypointRequest(player, position, dimension, action, arrivalRadius,
                route, waypointId);
        if (requestValidation != RouteOperationResult.SUCCESS) {
            return requestValidation;
        }
        return data.replaceWaypoint(routeId, waypointId,
                new Waypoint(waypointId, position, dimension, action, arrivalRadius), targetPosition)
                ? RouteOperationResult.SUCCESS
                : RouteOperationResult.WAYPOINT_NOT_FOUND;
    }

    public static RouteOperationResult convertWaypoint(ServerPlayer player, int routeId, int waypointId,
            WaypointAction action) {
        FarAndWideSavedData data = data(player);
        RouteOperationResult validation = validateWaypointMutation(player, data, routeId);
        if (validation != RouteOperationResult.SUCCESS) {
            return validation;
        }
        Waypoint waypoint = data.getWaypoint(routeId, waypointId);
        if (waypoint == null) {
            return RouteOperationResult.WAYPOINT_NOT_FOUND;
        }
        if (action == null || !waypoint.dimension().equals(dimension(player))
                || waypoint.position().distanceToSqr(player.position())
                        > Constants.Waypoints.EDIT_RADIUS * Constants.Waypoints.EDIT_RADIUS) {
            return RouteOperationResult.INVALID_WAYPOINT;
        }
        RouteOperationResult cargoValidation = validateCargoStations(player, waypoint.position(), action, waypoint.arrivalRadius(),
                data.getRoute(routeId), waypointId);
        if (cargoValidation != RouteOperationResult.SUCCESS) {
            return cargoValidation;
        }
        return data.convertWaypoint(routeId, waypointId, action)
                ? RouteOperationResult.SUCCESS
                : RouteOperationResult.WAYPOINT_NOT_FOUND;
    }

    public static RouteOperationResult deleteWaypoint(ServerPlayer player, int routeId, int waypointId) {
        FarAndWideSavedData data = data(player);
        RouteOperationResult validation = validateWaypointMutation(player, data, routeId);
        if (validation != RouteOperationResult.SUCCESS) {
            return validation;
        }
        Waypoint waypoint = data.getWaypoint(routeId, waypointId);
        if (waypoint == null) {
            return RouteOperationResult.WAYPOINT_NOT_FOUND;
        }
        if (!waypoint.dimension().equals(dimension(player))
                || waypoint.position().distanceToSqr(player.position())
                        > Constants.Waypoints.EDIT_RADIUS * Constants.Waypoints.EDIT_RADIUS) {
            return RouteOperationResult.INVALID_WAYPOINT;
        }
        return data.removeWaypointById(routeId, waypointId)
                ? RouteOperationResult.SUCCESS
                : RouteOperationResult.WAYPOINT_NOT_FOUND;
    }

    private static RouteOperationResult validateWaypointMutation(
            ServerPlayer player, FarAndWideSavedData data, int routeId) {
        if (data.getSelectedRouteId(assigneeId(player, data)) != routeId) {
            return RouteOperationResult.ROUTE_NOT_FOUND;
        }
        Entity assignee = controlledAssignee(player);
        RouteAssignment assignment = data.getAssignment(assigneeId(assignee, data));
        return assignment != null && assignment.getRouteId() == routeId && assignment.isActive()
                ? RouteOperationResult.ROUTE_ACTIVE
                : RouteOperationResult.SUCCESS;
    }

    private static RouteOperationResult validateWaypointRequest(ServerPlayer player, Vec3 position,
            net.minecraft.resources.Identifier requestedDimension, WaypointAction action, double arrivalRadius, Route route,
            int replacedWaypointId) {
        if (!(position != null && requestedDimension != null && action != null && Waypoint.isValidArrivalRadius(arrivalRadius)
                && Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z)
                && requestedDimension.equals(dimension(player))
                && position.distanceToSqr(player.position()) <= Constants.Waypoints.EDIT_RADIUS
                        * Constants.Waypoints.EDIT_RADIUS)) {
            return RouteOperationResult.INVALID_WAYPOINT;
        }
        return validateCargoStations(player, position, action, arrivalRadius, route, replacedWaypointId);
    }

    private static RouteOperationResult validateCargoStations(ServerPlayer player, Vec3 waypointPosition,
            WaypointAction action, double arrivalRadius, Route route, int replacedWaypointId) {
        if (!(action instanceof WaypointAction.Cargo cargo)) {
            return RouteOperationResult.SUCCESS;
        }
        if (cargo.behavior().usesSameStation() || conflictsWithRoute(cargo.behavior(), route, replacedWaypointId)) {
            return RouteOperationResult.SAME_CARGO_STATION;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return RouteOperationResult.INVALID_CARGO_STATION;
        }
        boolean valid = switch (cargo.behavior().operation()) {
            case LOAD -> validStation(level, waypointPosition, arrivalRadius, cargo.behavior().loadStation());
            case UNLOAD -> validStation(level, waypointPosition, arrivalRadius, cargo.behavior().unloadStation());
            case UNLOAD_THEN_LOAD -> validStation(level, waypointPosition, arrivalRadius, cargo.behavior().loadStation())
                    && validStation(level, waypointPosition, arrivalRadius, cargo.behavior().unloadStation());
        };
        return valid ? RouteOperationResult.SUCCESS : RouteOperationResult.INVALID_CARGO_STATION;
    }

    private static boolean conflictsWithRoute(CargoBehavior proposedBehavior, Route route, int replacedWaypointId) {
        return route != null && route.getWaypoints().stream()
                .filter(waypoint -> waypoint.id() != replacedWaypointId)
                .filter(waypoint -> waypoint.action() instanceof WaypointAction.Cargo)
                .map(waypoint -> ((WaypointAction.Cargo) waypoint.action()).behavior())
                .anyMatch(proposedBehavior::conflictsWithOppositeRole);
    }

    private static boolean validStation(ServerLevel level, Vec3 waypointPosition, double arrivalRadius,
            java.util.Optional<com.lastcallsoftware.farandwide.route.CargoStationBinding> station) {
        return CargoStationResolver.find(level, waypointPosition, arrivalRadius, station).isPresent();
    }

    private static void stopLoadedAssignees(ServerPlayer player, FarAndWideSavedData data, int routeId) {
        for (net.minecraft.server.level.ServerLevel level : player.level().getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                RouteAssignment assignment = entity.hasData(FarAndWideAttachments.ASSIGNEE_ID.get())
                        ? data.getAssignment(entity.getData(FarAndWideAttachments.ASSIGNEE_ID.get()))
                        : null;
                if (assignment != null && assignment.getRouteId() == routeId) {
                    ServerVehicleController.stop(entity);
                    VehicleChunkLoadingManager.release(entity);
                }
            }
        }
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
                look.x * Constants.Waypoints.PLACEMENT_OFFSET_DISTANCE,
                0.0,
                look.z * Constants.Waypoints.PLACEMENT_OFFSET_DISTANCE);
        return new Waypoint(position, dimension(player));
    }

    /** Transport-neutral data needed to build a route snapshot. */
    public record RouteState(List<Route> routes, int selectedRouteId) {
    }

    /** Assignment plus the runtime entity ID expected by the receiving client. */
    public record AssignmentState(int entityId, RouteAssignment assignment) {
    }
}
