package com.lastcallsoftware.farandwide.route.server;

import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.route.CargoBehavior;
import com.lastcallsoftware.farandwide.route.RouteOperationResult;
import com.lastcallsoftware.farandwide.route.TraversalType;
import com.lastcallsoftware.farandwide.route.Waypoint;
import com.lastcallsoftware.farandwide.route.WaypointAction;
import com.lastcallsoftware.farandwide.route.VehicleRouteAssignment;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideAttachments;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideSavedData;
import com.lastcallsoftware.farandwide.vehicle.server.ServerVehicleController;
import com.lastcallsoftware.farandwide.vehicle.server.VehicleChunkLoadingManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.eclipse.jdt.annotation.NonNull;

/**
 * Authoritative entry point for server-side route operations.
 *
 * <p>Network handlers call this class after decoding a request, while gameplay
 * controllers use it for server-originated changes such as death routes. Methods
 * resolve the correct world data and delegate permanent changes to
 * {@code FarAndWideSavedData}. Screens and other client code must never call this
 * class or assume a request succeeded before its server result arrives.
 *
 * <p>Player-requested mutations return a {@code RouteOperationResult}. Do
 * validation before the first mutation so a rejected request cannot leave
 * partially changed state.
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

    /** Records the player's latest death without changing their selected route. */
    public static Route recordPlayerDeath(ServerPlayer player) {
        FarAndWideSavedData data = data(player);
        return recordPlayerDeath(
                data,
                player.getUUID(),
                player.getGameProfile().name(),
                player.position(),
                dimension(player),
                routeId -> stopLoadedAssignees(player, data, routeId));
    }

    static Route recordPlayerDeath(FarAndWideSavedData data, UUID playerUuid, String playerName,
            Vec3 position, net.minecraft.resources.Identifier dimension,
            java.util.function.IntConsumer stopAssignees) {
        int existingRouteId = data.getDeathRouteId(playerUuid);
        if (existingRouteId > 0) {
            stopAssignees.accept(existingRouteId);
        }
        return data.upsertDeathRoute(
                playerUuid, playerName + "'s Death Route", new Waypoint(position, dimension));
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
        RouteOperationResult result = assignRoute(
                data,
                routeId,
                data.getSelectedRouteId(assigneeId(player, data)),
                assigneeId,
                assignee.position(),
                dimension(assignee),
                () -> {
                    ServerVehicleController.stop(assignee);
                    VehicleChunkLoadingManager.release(assignee);
                });
        if (result != RouteOperationResult.SUCCESS) {
            return result;
        }

        RouteAssignment assignment = data.getAssignment(assigneeId);
        // A successful request with no remaining assignment was the intentional
        // toggle-off case (same selected route, or no selected route).
        if (assignment == null) {
            return RouteOperationResult.SUCCESS;
        }
        if (assignee != player) {
            net.minecraft.resources.Identifier entityType =
                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(assignee.getType());
            data.registerVehicle(assignee.getUUID(), assigneeId, entityType.getPath());
            data.updateVehicleCustomName(
                    assignee.getUUID(), assignee.getCustomName() == null ? null : assignee.getCustomName().getString());
            data.updateVehicleLocation(assignee.getUUID(), dimension(assignee), assignee.blockPosition());
        }
        if (assignment.isActive()) {
            if (!VehicleChunkLoadingManager.update(assignee, assigneeId)) {
                data.setAssignmentActive(assigneeId, false);
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        RouteOperationResult.CHUNK_LOADING_LIMIT.translationKey()));
            }
        }
        return RouteOperationResult.SUCCESS;
    }

    static RouteOperationResult assignRoute(
            FarAndWideSavedData data,
            int requestedRouteId,
            int selectedRouteId,
            int assigneeId,
            Vec3 assigneePosition,
            net.minecraft.resources.Identifier assigneeDimension,
            Runnable stopExistingAssignment) {
        RouteAssignment existing = data.getAssignment(assigneeId);
        if (existing != null
                && (requestedRouteId == 0 || existing.getRouteId() == requestedRouteId)) {
            unassignRoute(data, assigneeId, stopExistingAssignment);
            return RouteOperationResult.SUCCESS;
        }
        Route route = data.getRoute(requestedRouteId);
        if (route == null) {
            return RouteOperationResult.ROUTE_NOT_FOUND;
        }
        if (route.getWaypoints().isEmpty()) {
            return RouteOperationResult.NO_WAYPOINTS;
        }
        if (selectedRouteId != requestedRouteId) {
            return RouteOperationResult.ROUTE_NOT_FOUND;
        }
        RouteAssignment assignment = data.assignRoute(
                requestedRouteId, assigneeId, assigneePosition, assigneeDimension);
        if (assignment == null) {
            return RouteOperationResult.NO_WAYPOINT_IN_DIMENSION;
        }
        if (existing != null && existing.isActive()) {
            stopExistingAssignment.run();
        }
        return RouteOperationResult.SUCCESS;
    }

    /** Moves only the requested vehicle assignment to an adjacent waypoint. */
    public static RouteOperationResult moveVehicleTargetWaypoint(ServerPlayer player, int assigneeId, int delta) {
        if (delta != -1 && delta != 1) {
            return RouteOperationResult.INVALID_WAYPOINT;
        }
        FarAndWideSavedData data = data(player);
        RouteAssignment assignment = data.getAssignment(assigneeId);
        Route route = assignment == null ? null : data.getRoute(assignment.getRouteId());
        if (!isManagedAssignee(player, data, assigneeId) || assignment == null) {
            return RouteOperationResult.NO_ASSIGNMENT;
        }
        int target = assignment.getTargetWaypointIndex() + delta;
        if (route == null || target < 0 || target >= route.getWaypoints().size()) {
            return RouteOperationResult.INVALID_WAYPOINT;
        }
        return data.updateAssignmentProgress(assigneeId, target, assignment.getTraversalDirection())
                ? RouteOperationResult.SUCCESS
                : RouteOperationResult.INVALID_WAYPOINT;
    }

    /** Reverses one managed assignee and immediately targets the route in its new direction. */
    public static RouteOperationResult reverseVehicleDirection(ServerPlayer player, int assigneeId) {
        FarAndWideSavedData data = data(player);
        if (!isManagedAssignee(player, data, assigneeId)) {
            return RouteOperationResult.NO_ASSIGNMENT;
        }
        return reverseVehicleDirection(data, assigneeId);
    }

    /** Reverses the ridden vehicle, or the requesting player's assignment while on foot. */
    public static RouteOperationResult reverseCurrentVehicleDirection(ServerPlayer player) {
        FarAndWideSavedData data = data(player);
        return reverseVehicleDirection(data, assigneeId(controlledAssignee(player), data));
    }

    static RouteOperationResult reverseVehicleDirection(FarAndWideSavedData data, int assigneeId) {
        RouteAssignment assignment = data.getAssignment(assigneeId);
        Route route = assignment == null ? null : data.getRoute(assignment.getRouteId());
        if (assignment == null) {
            return RouteOperationResult.NO_ASSIGNMENT;
        }
        if (route == null || route.getWaypoints().isEmpty()) {
            return RouteOperationResult.INVALID_WAYPOINT;
        }

        int direction = -assignment.getTraversalDirection();
        int waypointCount = route.getWaypoints().size();
        int target = assignment.getTargetWaypointIndex();
        if (waypointCount > 1) {
            int candidate = target + direction;
            target = assignment.getTraversalType(route) == TraversalType.LOOP
                    ? Math.floorMod(candidate, waypointCount)
                    : Math.clamp(candidate, 0, waypointCount - 1);
        }
        return data.updateAssignmentProgress(assigneeId, target, direction)
                ? RouteOperationResult.SUCCESS
                : RouteOperationResult.INVALID_WAYPOINT;
    }

    /** Sets one vehicle's active state without changing other assignments on its route. */
    public static RouteOperationResult setVehicleAssignmentActive(
            ServerPlayer player, int assigneeId, boolean active) {
        FarAndWideSavedData data = data(player);
        RouteAssignment assignment = data.getAssignment(assigneeId);
        if (!isManagedAssignee(player, data, assigneeId) || assignment == null) {
            return RouteOperationResult.NO_ASSIGNMENT;
        }
        if (assignment.isActive() == active) {
            return RouteOperationResult.SUCCESS;
        }
        if (assigneeId == assigneeId(player, data)) {
            data.setAssignmentActive(assigneeId, active);
            if (!active) {
                ServerVehicleController.stop(player);
            }
            return RouteOperationResult.SUCCESS;
        }
        return active
                ? activateVehicleAssignment(player, data, assigneeId)
                : deactivateVehicleAssignment(player, data, assigneeId);
    }

    /** Stops and removes one vehicle assignment, including any chunk-loading window. */
    public static RouteOperationResult unassignVehicle(ServerPlayer player, int assigneeId) {
        FarAndWideSavedData data = data(player);
        if (!isManagedAssignee(player, data, assigneeId) || data.getAssignment(assigneeId) == null) {
            return RouteOperationResult.NO_ASSIGNMENT;
        }
        if (assigneeId == assigneeId(player, data)) {
            return unassignRoute(data, assigneeId, () -> ServerVehicleController.stop(player))
                    ? RouteOperationResult.SUCCESS
                    : RouteOperationResult.NO_ASSIGNMENT;
        }
        UUID vehicleUuid = data.getVehicleUuid(assigneeId).orElse(null);
        Entity entity = vehicleUuid == null ? null : findLoadedEntity(player.level().getServer(), vehicleUuid);
        boolean removed = unassignRoute(data, assigneeId, () -> {
            if (entity != null) {
                ServerVehicleController.stop(entity);
                VehicleChunkLoadingManager.release(entity);
            } else if (vehicleUuid != null) {
                VehicleChunkLoadingManager.release(vehicleUuid);
            }
        });
        return removed ? RouteOperationResult.SUCCESS : RouteOperationResult.NO_ASSIGNMENT;
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
        return setRouteAssignmentsActive(player, routeId, active);
    }

    /** Toggles the ridden vehicle, or the player's own assignment when on foot. */
    public static RouteOperationResult toggleCurrentVehicle(ServerPlayer player) {
        Entity vehicle = controlledAssignee(player);
        FarAndWideSavedData data = data(player);
        int vehicleAssigneeId = assigneeId(vehicle, data);
        RouteAssignment assignment = data.getAssignment(vehicleAssigneeId);
        return assignment == null
                ? RouteOperationResult.NO_ASSIGNMENT
                : setVehicleAssignmentActive(player, vehicleAssigneeId, !assignment.isActive());
    }

    /** Sets every assignment on a specific route to the requested active state. */
    public static RouteOperationResult setRouteAssignmentsActive(ServerPlayer player, int routeId, boolean active) {
        FarAndWideSavedData data = data(player);
        if (data.getRoute(routeId) == null) {
            return RouteOperationResult.ROUTE_NOT_FOUND;
        }
        List<RouteAssignment> assignments = data.getAssignments().stream()
                .filter(assignment -> assignment.getRouteId() == routeId)
                .toList();
        if (assignments.isEmpty()) {
            return RouteOperationResult.NO_ASSIGNMENT;
        }
        if (!active) {
            data.setRouteAssignmentsActive(routeId, false);
            stopLoadedAssignees(player, data, routeId);
            return RouteOperationResult.SUCCESS;
        }

        RouteOperationResult firstFailure = RouteOperationResult.SUCCESS;
        for (RouteAssignment assignment : assignments) {
            if (assignment.isActive()) {
                continue;
            }
            RouteOperationResult result;
            if (data.isVehicleAssignee(assignment.getAssigneeId())) {
                result = activateVehicleAssignment(player, data, assignment.getAssigneeId());
            } else {
                data.setAssignmentActive(assignment.getAssigneeId(), true);
                result = RouteOperationResult.SUCCESS;
            }
            if (firstFailure == RouteOperationResult.SUCCESS && result != RouteOperationResult.SUCCESS) {
                firstFailure = result;
            }
        }
        return firstFailure;
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

    private static RouteOperationResult activateVehicleAssignment(
            ServerPlayer player, FarAndWideSavedData data, int assigneeId) {
        UUID vehicleUuid = data.getVehicleUuid(assigneeId).orElse(null);
        if (vehicleUuid == null) {
            return RouteOperationResult.NO_ASSIGNMENT;
        }
        Entity entity = findLoadedEntity(player.level().getServer(), vehicleUuid);
        if (entity != null) {
            data.setAssignmentActive(assigneeId, true);
            if (!VehicleChunkLoadingManager.update(entity, assigneeId)) {
                data.setAssignmentActive(assigneeId, false);
                return RouteOperationResult.CHUNK_LOADING_LIMIT;
            }
            return RouteOperationResult.SUCCESS;
        }

        FarAndWideSavedData.VehicleLocation location = data.getVehicleLocation(vehicleUuid).orElse(null);
        if (location == null) {
            return RouteOperationResult.VEHICLE_LOCATION_UNAVAILABLE;
        }
        data.setAssignmentActive(assigneeId, true);
        RouteOperationResult result = VehicleChunkLoadingManager.activateStoredVehicle(
                player.level().getServer(), vehicleUuid, assigneeId, location, player.getUUID());
        if (result != RouteOperationResult.SUCCESS) {
            data.setAssignmentActive(assigneeId, false);
        }
        return result;
    }

    private static RouteOperationResult deactivateVehicleAssignment(
            ServerPlayer player, FarAndWideSavedData data, int assigneeId) {
        UUID vehicleUuid = data.getVehicleUuid(assigneeId).orElse(null);
        if (vehicleUuid == null) {
            return RouteOperationResult.NO_ASSIGNMENT;
        }
        Entity entity = findLoadedEntity(player.level().getServer(), vehicleUuid);
        data.setAssignmentActive(assigneeId, false);
        if (entity != null) {
            ServerVehicleController.stop(entity);
            VehicleChunkLoadingManager.release(entity);
        } else {
            VehicleChunkLoadingManager.release(vehicleUuid);
        }
        return RouteOperationResult.SUCCESS;
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
        data.getVehicleRouteAssignments().stream()
                .filter(assignment -> assignment.routeId() == routeId)
                .map(assignment -> data.getVehicleUuid(assignment.assigneeId()))
                .flatMap((@NonNull Optional<UUID> vehicleUuid) -> vehicleUuid.stream())
                .forEach(VehicleChunkLoadingManager::release);
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

    public static List<VehicleRouteAssignment> getVehicleRouteAssignments(ServerPlayer player) {
        return getVehicleRouteAssignments(player.level().getServer());
    }

    /** Returns all vehicles plus the requesting player's own assignment, if present. */
    public static List<VehicleRouteAssignment> getRouteManagementAssignments(ServerPlayer player) {
        FarAndWideSavedData data = data(player);
        List<VehicleRouteAssignment> assignments = new java.util.ArrayList<>(
                getVehicleRouteAssignments(player.level().getServer()));
        int playerAssigneeId = assigneeId(player, data);
        RouteAssignment playerAssignment = data.getAssignment(playerAssigneeId);
        if (playerAssignment != null) {
            assignments.add(new VehicleRouteAssignment(
                    playerAssigneeId,
                    playerAssignment.getRouteId(),
                    player.getGameProfile().name() + " (Player)",
                    playerAssignment.getTargetWaypointIndex(),
                    playerAssignment.getTraversalDirection(),
                    playerAssignment.isActive()).withPosition(
                            player.level().dimension().identifier(), player.blockPosition()));
        }
        assignments.sort(java.util.Comparator.comparing(
                        (@NonNull VehicleRouteAssignment assignment) -> assignment.displayName())
                .thenComparingInt(
                        (@NonNull VehicleRouteAssignment assignment) -> assignment.assigneeId()));
        return List.copyOf(assignments);
    }

    public static List<VehicleRouteAssignment> getVehicleRouteAssignments(MinecraftServer server) {
        FarAndWideSavedData data = FarAndWideSavedData.get(server);
        return data.getVehicleRouteAssignments().stream()
                .map(assignment -> data.getVehicleUuid(assignment.assigneeId())
                        .map(vehicleUuid -> {
                            Entity entity = findLoadedEntity(server, vehicleUuid);
                            if (entity != null) {
                                String customName = entity.getCustomName() == null
                                        ? null
                                        : entity.getCustomName().getString();
                                data.updateVehicleCustomName(vehicleUuid, customName);
                                VehicleRouteAssignment namedAssignment = assignment.withDisplayName(
                                        data.getVehicleDisplayName(vehicleUuid).orElse(assignment.displayName()));
                                return namedAssignment.withPosition(
                                        entity.level().dimension().identifier(), entity.blockPosition());
                            }
                            return data.getVehicleLocation(vehicleUuid)
                                    .map(location -> assignment.withLastKnownPosition(
                                            location.dimension(), location.position()))
                                    .orElse(assignment);
                        })
                        .orElse(assignment))
                .toList();
    }

    /** Returns the runtime-addressed assignment when this persistent vehicle is loaded. */
    public static AssignmentState getLoadedVehicleAssignment(ServerPlayer player, int assigneeId) {
        FarAndWideSavedData data = data(player);
        RouteAssignment assignment = data.getAssignment(assigneeId);
        Entity entity = data.getVehicleUuid(assigneeId)
                .map(vehicleUuid -> findLoadedEntity(player.level().getServer(), vehicleUuid))
                .orElse(null);
        return assignment == null || entity == null ? null : new AssignmentState(entity.getId(), assignment);
    }

    /** Returns a runtime-addressed assignment for the requesting player or a loaded vehicle. */
    public static AssignmentState getLoadedManagedAssignment(ServerPlayer player, int assigneeId) {
        FarAndWideSavedData data = data(player);
        if (assigneeId == assigneeId(player, data)) {
            RouteAssignment assignment = data.getAssignment(assigneeId);
            return assignment == null ? null : new AssignmentState(player.getId(), assignment);
        }
        return getLoadedVehicleAssignment(player, assigneeId);
    }

    private static boolean isManagedAssignee(
            ServerPlayer player, FarAndWideSavedData data, int assigneeId) {
        return isManagedAssignee(data, assigneeId, assigneeId(player, data));
    }

    static boolean isManagedAssignee(
            FarAndWideSavedData data, int requestedAssigneeId, int playerAssigneeId) {
        return data.isVehicleAssignee(requestedAssigneeId)
                || requestedAssigneeId == playerAssigneeId;
    }

    private static Entity findLoadedEntity(MinecraftServer server, UUID entityUuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityUuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    /** Transport-neutral data needed to build a route snapshot. */
    public record RouteState(List<Route> routes, int selectedRouteId) {
    }

    /** Assignment plus the runtime entity ID expected by the receiving client. */
    public record AssignmentState(int entityId, RouteAssignment assignment) {
    }
}
