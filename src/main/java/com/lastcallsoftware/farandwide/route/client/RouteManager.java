package com.lastcallsoftware.farandwide.route.client;

import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.route.RouteOperationResult;
import com.lastcallsoftware.farandwide.route.TraversalType;
import com.lastcallsoftware.farandwide.route.Waypoint;
import com.lastcallsoftware.farandwide.route.WaypointAction;
import com.lastcallsoftware.farandwide.route.network.client.RouteRequests;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Client-facing route API and replaceable snapshot cache.
 *
 * <p>The lists in this class are not authoritative. They are discarded when the
 * connection changes and replaced only by server snapshots. Public mutation
 * methods send requests through {@code RouteRequests}; they must not edit cached
 * domain records as if a server operation had already succeeded.
 *
 * <p>Screens, commands, HUD code, rendering, and vehicle controls should use this
 * class rather than importing payloads or networking APIs directly.
 */
public class RouteManager {
    private static List<Route> routes = new ArrayList<>();
    private static final Map<Integer, RouteAssignment> assignmentsByEntity = new HashMap<>();
    private static Route selectedRoute = null;
    private static long routeStateRevision;
    private static boolean requestedServerSnapshot;
    private static int requestedAssignmentEntityId = Integer.MIN_VALUE;

    private RouteManager() {
        // Private constructor to prevent instantiation
    }

    public static List<Route> getRoutes() {
        requestServerSnapshot();
        return new ArrayList<>(routes);
    }

    public static long getRouteStateRevision() {
        return routeStateRevision;
    }

    /** Resets all client-only state before connecting to a different world/server. */
    public static void clearClientState() {
        routes.clear();
        assignmentsByEntity.clear();
        selectedRoute = null;
        requestedServerSnapshot = false;
        requestedAssignmentEntityId = Integer.MIN_VALUE;
        WaypointEditor.reset();
        CargoStationSelector.reset();
        routeStateRevision++;
    }

    /** Requests fresh authoritative state once the client connection is ready. */
    public static void onClientLoggedIn() {
        clearClientState();
        requestServerSnapshot();
        // The login event fires before the local player's runtime entity ID is assigned.
        // Assignment state is requested on the first normal navigation/HUD access instead.
    }

    /** Replaces the local read cache after an authoritative server snapshot arrives. */
    public static void replaceRoutesFromServer(List<Route> serverRoutes, int selectedRouteId) {
        routes = new ArrayList<>(serverRoutes);
        if (selectedRouteId > 0) {
            selectedRoute = getRoute(selectedRouteId);
        } else if (selectedRoute != null) {
            // Broadcast snapshots omit the player-specific selection. Preserve
            // it by resolving the old ID against the newly received records.
            selectedRoute = getRoute(selectedRoute.getId());
        }
        routeStateRevision++;
    }

    private static void requestServerSnapshot() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!requestedServerSnapshot && minecraft.getConnection() != null) {
            requestedServerSnapshot = true;
            RouteRequests.requestRoutes();
        }
    }

    public static Route getCurrentRoute() {
        return selectedRoute;
    }

    public static int getCurrentRouteId() {
        return selectedRoute == null ? 0 : selectedRoute.getId();
    }

    public static Route getRoute(String name) {
        for (Route route : routes) {
            if (route.getName().equals(name)) {
                return route;
            }
        }
        return null;
    }

    public static Route getRoute(int id) {
        for (Route route : routes) {
            if (route.getId() == id) {
                return route;
            }
        }
        return null;
    }

    public static void setSelectedRoute(Route route) {
        if (routes.contains(route)) {
            selectedRoute = route;
            routeStateRevision++;
            RouteRequests.selectRoute(route.getId());
        }
    }

    public static void setSelectedRoute(String name) {
        Route route = getRoute(name);
        if (route != null) {
            setSelectedRoute(route);
        }
    }

    public static void setSelectedRoute(int id) {
        Route route = getRoute(id);
        if (route != null) {
            setSelectedRoute(route);
        }
    }

    /** Clears the player's selected route without changing any route assignment. */
    public static void clearSelectedRoute() {
        if (selectedRoute == null) {
            return;
        }
        selectedRoute = null;
        routeStateRevision++;
        RouteRequests.selectRoute(0);
    }

    public static void createRoute(String name, TraversalType traversalType) {
        RouteRequests.createRoute(name, traversalType);
    }

    public static void removeRoute(Route route) {
        RouteRequests.deleteRoute(route.getId());
    }

    public static void removeRoute(String name) {
        Route route = getRoute(name);
        if (route != null) {
            removeRoute(route);
        }
    }

    public static void removeRoute(int id) {
        Route route = getRoute(id);
        if (route != null) {
            removeRoute(route);
        }
    }

    public static void assignRoute(Route route) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (route.getWaypoints().isEmpty()) {
            sendOverlayMessage("message.farandwide.route_has_no_waypoints");
            return;
        }

        RouteRequests.assignRoute(route.getId());
        sendOverlayMessage("message.farandwide.route_assigned", route.getName());
    }

    /** Assigns the selected route, or unassigns the controlled assignee when one is already assigned. */
    public static void toggleRouteAssignment() {
        Route route = getCurrentRoute();
        RouteRequests.assignRoute(route == null ? 0 : route.getId());
    }

    public static void updateRoute(Route route, String name, TraversalType traversalType) {
        RouteRequests.updateRoute(route.getId(), name, traversalType);
    }

    public static void addCurrentPosition(Route route) {
        RouteRequests.addWaypoint(route.getId());
    }

    public static void createWaypoint(Route route, Vec3 position, Identifier dimension, WaypointAction action) {
        RouteRequests.createWaypoint(route.getId(), position, dimension, action);
    }

    public static void createWaypoint(Route route, Vec3 position, Identifier dimension, WaypointAction action,
            double arrivalRadius) {
        RouteRequests.createWaypoint(route.getId(), position, dimension, action, arrivalRadius);
    }

    public static void replaceWaypoint(Route route, Waypoint waypoint, int targetPosition) {
        RouteRequests.replaceWaypoint(route.getId(), waypoint, targetPosition);
    }

    public static void convertWaypoint(Route route, int waypointId, WaypointAction action) {
        RouteRequests.convertWaypoint(route.getId(), waypointId, action);
    }

    public static void deleteWaypoint(Route route, int waypointId) {
        RouteRequests.deleteWaypoint(route.getId(), waypointId);
    }

    public static void replaceAssignmentFromServer(int entityId, RouteAssignment assignment) {
        if (assignment == null) {
            assignmentsByEntity.remove(entityId);
        } else {
            assignmentsByEntity.put(entityId, assignment);
        }
    }

    public static void handleOperationResult(RouteOperationResult result) {
        if (result != RouteOperationResult.SUCCESS) {
            sendOverlayMessage(result.translationKey());
        }
    }

    public static RouteAssignment getAssignment(int assigneeEntityId) {
        return assignmentsByEntity.get(assigneeEntityId);
    }

    public static List<RouteAssignment> getAssignments() {
        return new ArrayList<>(assignmentsByEntity.values());
    }

    public static RouteAssignment getActiveAssignment(Entity assignee) {
        RouteAssignment assignment = assignee == null ? null : getAssignment(assignee.getId());
        return assignment != null && assignment.isActive() ? assignment : null;
    }

    public static RouteAssignment getNavigationAssignment() {
        requestAssignmentSnapshot();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return null;
        }

        Entity riddenEntity = minecraft.player.getVehicle();
        RouteAssignment riddenAssignment = riddenEntity == null ? null : getAssignment(riddenEntity.getId());
        if (riddenAssignment != null) {
            return riddenAssignment;
        }
        return getAssignment(minecraft.player.getId());
    }

    public static void synchronizeSelectedRouteWithNavigation() {
        RouteAssignment assignment = getNavigationAssignment();
        Route assignedRoute = assignment == null ? null : getRoute(assignment.getRouteId());
        if (assignedRoute != null) {
            selectedRoute = assignedRoute;
        }
    }

    public static Waypoint getTargetWaypoint(RouteAssignment assignment) {
        if (assignment == null) {
            return null;
        }
        Route route = getRoute(assignment.getRouteId());
        int index = assignment.getTargetWaypointIndex();
        return route == null || index < 0 || index >= route.getWaypoints().size()
                ? null
                : route.getWaypoints().get(index);
    }

    public static void toggleCurrentAssignment() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        RouteRequests.toggleAssignment();
    }

    private static void requestAssignmentSnapshot() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }
        Entity assignee = minecraft.player.getVehicle() == null ? minecraft.player : minecraft.player.getVehicle();
        if (requestedAssignmentEntityId != assignee.getId()) {
            // Request again when mounting or dismounting because the controlled
            // assignee, and therefore the relevant assignment, has changed.
            requestedAssignmentEntityId = assignee.getId();
            RouteRequests.requestAssignment();
        }
    }

    private static void sendOverlayMessage(String translationKey, Object... args) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendOverlayMessage(Component.translatable(translationKey, args));
        }
    }
}
