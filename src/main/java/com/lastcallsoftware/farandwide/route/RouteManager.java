package com.lastcallsoftware.farandwide.route;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class RouteManager {
    private static List<Route> routes = new ArrayList<>();
    private static final Map<Integer, RouteAssignment> assignmentsByVehicle = new HashMap<>();
    private static Route selectedRoute = null;

    private RouteManager() {
        // Private constructor to prevent instantiation
    }

    public static List<Route> getRoutes() {
        return new ArrayList<>(routes);
    }

    public static Route getCurrentRoute() {
        return selectedRoute;
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
        }
    }

    public static void setSelectedRoute(String name) {
        Route route = getRoute(name);
        if (route != null) {
            selectedRoute = route;
        }
    }

    public static void setSelectedRoute(int id) {
        Route route = getRoute(id);
        if (route != null) {
            selectedRoute = route;
        }
    }

    public static void addRoute(Route route) {
        routes.add(route);
        if (selectedRoute == null) {
            selectedRoute = route;
        }
    }

    public static void removeRoute(Route route) {
        routes.remove(route);
        assignmentsByVehicle.values().removeIf(assignment -> assignment.getRouteId() == route.getId());
        if (selectedRoute == route) {
            selectedRoute = routes.isEmpty() ? null : routes.get(0);
        }
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
        Entity vehicle = minecraft.player == null ? null : minecraft.player.getVehicle();
        if (vehicle == null) {
            sendOverlayMessage("message.farandwide.no_vehicle");
            return;
        }
        if (route.getWaypoints().isEmpty()) {
            sendOverlayMessage("message.farandwide.route_has_no_waypoints");
            return;
        }

        int targetIndex = findNearestWaypointIndex(route, vehicle.position());
        RouteAssignment assignment = new RouteAssignment(route.getId(), vehicle.getId(), targetIndex);
        assignmentsByVehicle.put(vehicle.getId(), assignment);
        sendOverlayMessage("message.farandwide.route_assigned", route.getName());
    }

    public static RouteAssignment getAssignment(int vehicleId) {
        return assignmentsByVehicle.get(vehicleId);
    }

    public static RouteAssignment getActiveAssignment(Entity vehicle) {
        RouteAssignment assignment = vehicle == null ? null : getAssignment(vehicle.getId());
        return assignment != null && assignment.isActive() ? assignment : null;
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
        Entity vehicle = minecraft.player == null ? null : minecraft.player.getVehicle();
        RouteAssignment assignment = vehicle == null ? null : getAssignment(vehicle.getId());
        if (assignment == null) {
            sendOverlayMessage("message.farandwide.no_route_assignment");
            return;
        }
        assignment.setActive(!assignment.isActive());
    }

    private static int findNearestWaypointIndex(Route route, Vec3 position) {
        int nearestIndex = 0;
        double nearestDistance = Double.MAX_VALUE;
        for (int index = 0; index < route.getWaypoints().size(); index++) {
            double distance = route.getWaypoints().get(index).position().distanceToSqr(position);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestIndex = index;
            }
        }
        return nearestIndex;
    }

    private static void sendOverlayMessage(String translationKey, Object... args) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendOverlayMessage(Component.translatable(translationKey, args));
        }
    }
}
