package com.lastcallsoftware.farandwide.route.persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import com.lastcallsoftware.farandwide.FarAndWide;
import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.route.TraversalType;
import com.lastcallsoftware.farandwide.route.Waypoint;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

/**
 * Server-owned, world-scoped route storage.
 *
 * <p>This is the only class that changes permanent route or assignment values.
 * Every successful mutation in this class must call {@link #setDirty()}, or the
 * change may appear during play but disappear after the world is reloaded.
 * Reads return immutable copies so callers cannot mutate the backing collections.
 *
 * <p>Disk representation belongs to {@link RouteCodecs}; request validation and
 * player/entity convenience logic belong to {@code RouteService}. Keeping those
 * concerns out of this class makes its contract narrow: store, find, replace,
 * allocate IDs, and mark changed data dirty.
 */
public final class FarAndWideSavedData extends SavedData {
    public static final int CURRENT_DATA_VERSION = 1;
    private static final Identifier ID = Identifier.fromNamespaceAndPath(FarAndWide.MODID, "routes");

    public static final SavedDataType<FarAndWideSavedData> TYPE = new SavedDataType<>(
            ID, FarAndWideSavedData::new, RouteCodecs.SAVED_DATA, null);

    private final List<Route> routes = new ArrayList<>();
    private final Map<Integer, RouteAssignment> assignmentsByAssignee = new HashMap<>();
    private final Map<Integer, Integer> selectedRouteByAssignee = new HashMap<>();
    private int nextRouteId = 1;
    private int nextAssigneeId = 1;

    public static FarAndWideSavedData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<Route> getRoutes() { return List.copyOf(routes); }
    public List<RouteAssignment> getAssignments() { return List.copyOf(assignmentsByAssignee.values()); }
    public Map<Integer, RouteAssignment> getAssignmentsByAssignee() { return Map.copyOf(assignmentsByAssignee); }
    Map<Integer, Integer> getSelectedRoutesByAssignee() { return Map.copyOf(selectedRouteByAssignee); }
    public int getNextRouteId() { return nextRouteId; }
    public int getNextAssigneeId() { return nextAssigneeId; }

    public Route createRoute() {
        Route route = new Route(nextRouteId++, "New Route", TraversalType.ONE_WAY, List.of());
        routes.add(route);
        setDirty();
        return route;
    }

    public Route getRoute(int routeId) {
        return routes.stream().filter(route -> route.getId() == routeId).findFirst().orElse(null);
    }

    public RouteAssignment getAssignment(int assigneeId) {
        return assignmentsByAssignee.get(assigneeId);
    }

    public int getSelectedRouteId(int assigneeId) {
        return selectedRouteByAssignee.getOrDefault(assigneeId, 0);
    }

    public boolean setSelectedRouteId(int assigneeId, int routeId) {
        if (getRoute(routeId) == null) {
            return false;
        }
        if (getSelectedRouteId(assigneeId) == routeId) {
            return false;
        }
        selectedRouteByAssignee.put(assigneeId, routeId);
        setDirty();
        return true;
    }

    /** Clears the route selection for an assignee. */
    public boolean clearSelectedRouteId(int assigneeId) {
        if (selectedRouteByAssignee.remove(assigneeId) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public RouteAssignment assignRoute(int routeId, int assigneeId, Vec3 assigneePosition, Identifier dimension) {
        Route route = getRoute(routeId);
        if (route == null || route.getWaypoints().isEmpty()) {
            return null;
        }
        int targetWaypointIndex = findNearestWaypointIndex(route, assigneePosition, dimension);
        if (targetWaypointIndex < 0) {
            return null;
        }
        RouteAssignment assignment = new RouteAssignment(routeId, assigneeId, targetWaypointIndex);
        assignmentsByAssignee.put(assigneeId, assignment);
        setDirty();
        return assignment;
    }

    public boolean setAssignmentActive(int assigneeId, boolean active) {
        RouteAssignment assignment = getAssignment(assigneeId);
        if (assignment == null || assignment.isActive() == active) {
            return false;
        }
        assignmentsByAssignee.put(assigneeId, new RouteAssignment(
                assignment.getRouteId(), assignment.getAssigneeId(), assignment.getTargetWaypointIndex(),
                assignment.getTraversalDirection(), assignment.getTraversalTypeOverride(), active));
        setDirty();
        return true;
    }

    public boolean updateAssignmentProgress(int assigneeId, int targetWaypointIndex, int traversalDirection) {
        RouteAssignment assignment = getAssignment(assigneeId);
        if (assignment == null) {
            return false;
        }
        assignmentsByAssignee.put(assigneeId, new RouteAssignment(
                assignment.getRouteId(), assignment.getAssigneeId(), targetWaypointIndex,
                traversalDirection, assignment.getTraversalTypeOverride(), assignment.isActive()));
        setDirty();
        return true;
    }

    public boolean setAssignmentTraversalTypeOverride(int assigneeId, TraversalType traversalTypeOverride) {
        RouteAssignment assignment = getAssignment(assigneeId);
        if (assignment == null || assignment.getTraversalTypeOverride() == traversalTypeOverride) {
            return false;
        }
        assignmentsByAssignee.put(assigneeId, new RouteAssignment(
                assignment.getRouteId(), assignment.getAssigneeId(), assignment.getTargetWaypointIndex(),
                assignment.getTraversalDirection(), traversalTypeOverride, assignment.isActive()));
        setDirty();
        return true;
    }

    public boolean renameRoute(int routeId, String name) {
        Route route = getRoute(routeId);
        if (route == null || name == null || name.isBlank()) {
            return false;
        }
        replaceRoute(route, new Route(route.getId(), name.trim(), route.getTraversalType(), route.getWaypoints()));
        setDirty();
        return true;
    }

    public boolean setTraversalType(int routeId, TraversalType traversalType) {
        Route route = getRoute(routeId);
        if (route == null || traversalType == null) {
            return false;
        }
        replaceRoute(route, new Route(route.getId(), route.getName(), traversalType, route.getWaypoints()));
        setDirty();
        return true;
    }

    public boolean addWaypoint(int routeId, Waypoint waypoint) {
        Route route = getRoute(routeId);
        if (route == null || waypoint == null) {
            return false;
        }
        List<Waypoint> waypoints = new ArrayList<>(route.getWaypoints());
        waypoints.add(waypoint);
        replaceRoute(route, new Route(route.getId(), route.getName(), route.getTraversalType(), waypoints));
        setDirty();
        return true;
    }

    public boolean removeWaypoint(int routeId, int waypointIndex) {
        Route route = getRoute(routeId);
        if (route == null || waypointIndex < 0 || waypointIndex >= route.getWaypoints().size()) {
            return false;
        }
        List<Waypoint> waypoints = new ArrayList<>(route.getWaypoints());
        waypoints.remove(waypointIndex);
        replaceRoute(route, new Route(route.getId(), route.getName(), route.getTraversalType(), waypoints));
        setDirty();
        return true;
    }

    public boolean removeNearestWaypoint(int routeId, Vec3 position, Identifier dimension, double maxDistance) {
        Route route = getRoute(routeId);
        if (route == null) {
            return false;
        }
        double maxDistanceSquared = maxDistance * maxDistance;
        for (int index = 0; index < route.getWaypoints().size(); index++) {
            Waypoint waypoint = route.getWaypoints().get(index);
            if (waypoint.dimension().equals(dimension) && waypoint.position().distanceToSqr(position) <= maxDistanceSquared) {
                return removeWaypoint(routeId, index);
            }
        }
        return false;
    }

    public boolean toggleWaypoint(int routeId, Vec3 position, Identifier dimension, double maxDistance,
            Waypoint waypointToAdd) {
        if (removeNearestWaypoint(routeId, position, dimension, maxDistance)) {
            return true;
        }
        return addWaypoint(routeId, waypointToAdd);
    }

    public boolean deleteRoute(int routeId) {
        boolean removed = routes.removeIf(route -> route.getId() == routeId);
        if (!removed) {
            return false;
        }
        assignmentsByAssignee.values().removeIf(assignment -> assignment.getRouteId() == routeId);
        selectedRouteByAssignee.values().removeIf(selectedRouteId -> selectedRouteId == routeId);
        setDirty();
        return true;
    }

    public int allocateAssigneeId() {
        // Allocation itself changes permanent state. Mark dirty even if no
        // assignment is created afterward, otherwise a reload could reuse the ID.
        setDirty();
        return nextAssigneeId++;
    }

    private void replaceRoute(Route oldRoute, Route newRoute) {
        routes.set(routes.indexOf(oldRoute), newRoute);
    }

    private static int findNearestWaypointIndex(Route route, Vec3 position, Identifier dimension) {
        int nearestIndex = 0;
        double nearestDistance = Double.MAX_VALUE;
        for (int index = 0; index < route.getWaypoints().size(); index++) {
            Waypoint waypoint = route.getWaypoints().get(index);
            if (!waypoint.dimension().equals(dimension)) {
                continue;
            }
            double distance = waypoint.position().distanceToSqr(position);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestIndex = index;
            }
        }
        return nearestDistance == Double.MAX_VALUE ? -1 : nearestIndex;
    }

    static FarAndWideSavedData restore(int dataVersion, int savedNextRouteId, int savedNextAssigneeId,
            List<Route> routes, Map<Integer, RouteAssignment> assignments, Map<Integer, Integer> selectedRoutes) {
        /*
         * Repair records independently instead of rejecting the complete save.
         * A damaged assignment must not destroy unrelated valid routes. Repairs
         * set the dirty flag so the corrected representation replaces the bad
         * one during the next normal world save.
         */
        FarAndWideSavedData data = new FarAndWideSavedData();
        boolean repaired = false;
        Set<Integer> routeIds = new HashSet<>();
        for (Route route : routes) {
            if (route.getId() <= 0 || !routeIds.add(route.getId())) {
                FarAndWide.LOGGER.warn("Skipping route with invalid or duplicate ID {}", route.getId());
                repaired = true;
                continue;
            }
            data.routes.add(route);
        }

        for (Map.Entry<Integer, RouteAssignment> entry : assignments.entrySet()) {
            int assigneeId = entry.getKey();
            RouteAssignment assignment = entry.getValue();
            Route route = data.getRoute(assignment.getRouteId());
            if (assigneeId <= 0 || route == null || route.getWaypoints().isEmpty()) {
                FarAndWide.LOGGER.warn("Skipping invalid assignment for assignee {} and route {}",
                        assigneeId, assignment.getRouteId());
                repaired = true;
                continue;
            }

            int targetIndex = Math.clamp(assignment.getTargetWaypointIndex(), 0, route.getWaypoints().size() - 1);
            int direction = assignment.getTraversalDirection() == -1 ? -1 : 1;
            if (assignment.getAssigneeId() != assigneeId
                    || targetIndex != assignment.getTargetWaypointIndex()
                    || direction != assignment.getTraversalDirection()) {
                FarAndWide.LOGGER.warn("Repairing assignment for assignee {}", assigneeId);
                repaired = true;
            }
            data.assignmentsByAssignee.put(assigneeId, new RouteAssignment(
                    assignment.getRouteId(), assigneeId, targetIndex, direction,
                    assignment.getTraversalTypeOverride(), assignment.isActive()));
        }

        selectedRoutes.forEach((assigneeId, routeId) -> {
            if (assigneeId > 0 && data.getRoute(routeId) != null) {
                data.selectedRouteByAssignee.put(assigneeId, routeId);
            } else {
                FarAndWide.LOGGER.warn("Skipping stale selected route {} for assignee {}", routeId, assigneeId);
            }
        });
        if (data.selectedRouteByAssignee.size() != selectedRoutes.size()) {
            repaired = true;
        }

        int highestRouteId = data.routes.stream().mapToInt(Route::getId).max().orElse(0);
        // Include rejected records when finding the allocator floor. Their IDs
        // may still exist on entity attachments, so reusing one would alias two
        // logically different assignees.
        int highestAssignmentId = assignments.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        int highestSelectionId = selectedRoutes.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        int highestAssigneeId = Math.max(highestAssignmentId, highestSelectionId);
        data.nextRouteId = Math.max(savedNextRouteId, highestRouteId + 1);
        data.nextAssigneeId = Math.max(savedNextAssigneeId, highestAssigneeId + 1);
        if (data.nextRouteId != savedNextRouteId || data.nextAssigneeId != savedNextAssigneeId) {
            FarAndWide.LOGGER.warn("Repairing stale route or assignee ID allocator");
            repaired = true;
        }
        if (dataVersion > CURRENT_DATA_VERSION) {
            FarAndWide.LOGGER.warn("Route data version {} is newer than supported version {}", dataVersion, CURRENT_DATA_VERSION);
        }
        if (repaired) {
            data.setDirty();
        }
        return data;
    }
}
