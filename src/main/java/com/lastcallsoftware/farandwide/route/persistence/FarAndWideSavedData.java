package com.lastcallsoftware.farandwide.route.persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.FarAndWide;
import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.route.TraversalType;
import com.lastcallsoftware.farandwide.route.Waypoint;
import com.lastcallsoftware.farandwide.route.WaypointAction;
import com.lastcallsoftware.farandwide.route.VehicleRouteAssignment;

import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.eclipse.jdt.annotation.NonNull;

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
    private static final Identifier ID = Identifier.fromNamespaceAndPath(FarAndWide.MODID, "routes");

    public static final SavedDataType<FarAndWideSavedData> TYPE = new SavedDataType<>(
            ID, FarAndWideSavedData::new, RouteCodecs.SAVED_DATA, null);

    private final List<Route> routes = new ArrayList<>();
    private final Map<Integer, RouteAssignment> assignmentsByAssignee = new HashMap<>();
    private final Map<Integer, Integer> selectedRouteByAssignee = new HashMap<>();
    private final Map<UUID, Integer> vehicleAssigneeByUuid = new HashMap<>();
    private final Map<UUID, VehicleIdentity> vehicleIdentityByUuid = new HashMap<>();
    private final Map<UUID, VehicleLocation> vehicleLocationByUuid = new HashMap<>();
    private int nextRouteId = 1;
    private int nextAssigneeId = 1;
    private int nextWaypointId = 1;

    public static FarAndWideSavedData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<Route> getRoutes() { return List.copyOf(routes); }
    public List<RouteAssignment> getAssignments() { return List.copyOf(assignmentsByAssignee.values()); }
    public Map<Integer, RouteAssignment> getAssignmentsByAssignee() { return Map.copyOf(assignmentsByAssignee); }
    Map<Integer, Integer> getSelectedRoutesByAssignee() { return Map.copyOf(selectedRouteByAssignee); }
    Map<UUID, Integer> getVehicleAssigneesByUuid() { return Map.copyOf(vehicleAssigneeByUuid); }
    Map<UUID, VehicleIdentity> getVehicleIdentitiesByUuid() { return Map.copyOf(vehicleIdentityByUuid); }
    Map<UUID, VehicleLocation> getVehicleLocationsByUuid() { return Map.copyOf(vehicleLocationByUuid); }
    public int getNextRouteId() { return nextRouteId; }
    public int getNextAssigneeId() { return nextAssigneeId; }
    public int getNextWaypointId() { return nextWaypointId; }

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

    /** Returns the stable assignee ID associated with a persistent vehicle UUID. */
    public int getVehicleAssigneeId(UUID vehicleUuid) {
        return vehicleAssigneeByUuid.getOrDefault(vehicleUuid, 0);
    }

    /** Resolves the persistent vehicle UUID associated with an assignment. */
    public Optional<UUID> getVehicleUuid(int assigneeId) {
        return vehicleAssigneeByUuid.entrySet().stream()
                .filter(entry -> entry.getValue() == assigneeId)
                .map((Map.@NonNull Entry<UUID, Integer> entry) -> entry.getKey())
                .findFirst();
    }

    public Optional<VehicleLocation> getVehicleLocation(UUID vehicleUuid) {
        return Optional.ofNullable(vehicleLocationByUuid.get(vehicleUuid));
    }

    public Optional<String> getVehicleDisplayName(UUID vehicleUuid) {
        return Optional.ofNullable(vehicleIdentityByUuid.get(vehicleUuid))
                .map((FarAndWideSavedData.@NonNull VehicleIdentity identity) -> identity.displayName());
    }

    /** Records a restart location, avoiding dirty writes when the value has not changed. */
    public boolean updateVehicleLocation(UUID vehicleUuid, Identifier dimension, BlockPos position) {
        if (!vehicleAssigneeByUuid.containsKey(vehicleUuid) || dimension == null || position == null) {
            return false;
        }
        VehicleLocation location = new VehicleLocation(dimension, position.immutable());
        if (location.equals(vehicleLocationByUuid.get(vehicleUuid))) {
            return false;
        }
        vehicleLocationByUuid.put(vehicleUuid, location);
        setDirty();
        return true;
    }

    public boolean isVehicleAssignee(int assigneeId) {
        return vehicleAssigneeByUuid.containsValue(assigneeId);
    }

    /** Returns every vehicle-backed assignment without exposing persistent UUIDs to clients. */
    public List<VehicleRouteAssignment> getVehicleRouteAssignments() {
        return vehicleAssigneeByUuid.entrySet().stream()
                .map(entry -> {
                    RouteAssignment assignment = getAssignment(entry.getValue());
                    VehicleIdentity identity = vehicleIdentityByUuid.get(entry.getKey());
                    if (assignment == null) {
                        return null;
                    }
                    String name = identity == null
                            ? "Vehicle " + entry.getValue()
                            : identity.displayName();
                    return new VehicleRouteAssignment(
                            entry.getValue(), assignment.getRouteId(), name,
                            assignment.getTargetWaypointIndex(), assignment.getTraversalDirection(),
                            assignment.isActive(), Optional.empty());
                })
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing(
                                (@NonNull VehicleRouteAssignment assignment) -> assignment.displayName())
                        .thenComparingInt(
                                (@NonNull VehicleRouteAssignment assignment) -> assignment.assigneeId()))
                .toList();
    }

    /** Records the identity bridge required to validate entity-owned chunk tickets. */
    public boolean associateVehicle(UUID vehicleUuid, int assigneeId) {
        if (vehicleUuid == null || assigneeId <= 0 || getAssignment(assigneeId) == null) {
            return false;
        }
        Set<UUID> replacedVehicles = vehicleAssigneeByUuid.entrySet().stream()
                .filter(entry -> entry.getValue() == assigneeId && !entry.getKey().equals(vehicleUuid))
                .map((Map.@NonNull Entry<UUID, Integer> entry) -> entry.getKey())
                .collect(java.util.stream.Collectors.toSet());
        boolean changed = vehicleAssigneeByUuid.keySet().removeAll(replacedVehicles);
        vehicleLocationByUuid.keySet().removeAll(replacedVehicles);
        Integer previous = vehicleAssigneeByUuid.put(vehicleUuid, assigneeId);
        changed |= previous == null || previous != assigneeId;
        if (changed) {
            setDirty();
        }
        return changed;
    }

    /**
     * Associates an assignment with a vehicle and allocates its permanent friendly
     * name the first time that UUID is encountered. Previously generated names are
     * refreshed when an entity type gains a more specific normalization rule.
     */
    public boolean registerVehicle(UUID vehicleUuid, int assigneeId, String vehicleTypeKey) {
        if (vehicleUuid == null || getAssignment(assigneeId) == null
                || vehicleTypeKey == null || vehicleTypeKey.isBlank()) {
            return false;
        }
        boolean changed = associateVehicle(vehicleUuid, assigneeId);
        String normalizedType = normalizeVehicleType(vehicleTypeKey);
        if (!vehicleIdentityByUuid.containsKey(vehicleUuid)) {
            int number = vehicleIdentityByUuid.values().stream()
                    .filter(identity -> normalizeVehicleType(identity.typeKey()).equals(normalizedType))
                    .mapToInt((FarAndWideSavedData.@NonNull VehicleIdentity identity) -> identity.number())
                    .max()
                    .orElse(0) + 1;
            vehicleIdentityByUuid.put(vehicleUuid, new VehicleIdentity(
                    normalizedType, number, createDisplayName(normalizedType, number)));
            setDirty();
            changed = true;
        } else {
            VehicleIdentity identity = vehicleIdentityByUuid.get(vehicleUuid);
            String oldGeneratedName = createDisplayName(identity.typeKey(), identity.number());
            if (!identity.typeKey().equals(normalizedType)) {
                int number = identity.number();
                boolean numberInUse = vehicleIdentityByUuid.entrySet().stream()
                        .anyMatch(entry -> !entry.getKey().equals(vehicleUuid)
                                && normalizeVehicleType(entry.getValue().typeKey()).equals(normalizedType)
                                && entry.getValue().number() == identity.number());
                if (numberInUse) {
                    number = vehicleIdentityByUuid.values().stream()
                            .filter(other -> normalizeVehicleType(other.typeKey()).equals(normalizedType))
                            .mapToInt((FarAndWideSavedData.@NonNull VehicleIdentity other) -> other.number())
                            .max()
                            .orElse(0) + 1;
                }
                String displayName = identity.displayName().equals(oldGeneratedName)
                        ? createDisplayName(normalizedType, number)
                        : identity.displayName();
                vehicleIdentityByUuid.put(vehicleUuid, new VehicleIdentity(
                        normalizedType, number, displayName));
                setDirty();
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Mirrors an entity custom name for management display while it is unloaded.
     * A blank name restores the generated type-and-number fallback.
     */
    public boolean updateVehicleCustomName(UUID vehicleUuid, String customName) {
        VehicleIdentity identity = vehicleIdentityByUuid.get(vehicleUuid);
        if (identity == null) {
            return false;
        }
        String displayName = customName == null || customName.isBlank()
                ? createDisplayName(identity.typeKey(), identity.number())
                : customName.substring(0, Math.min(customName.length(), Constants.Network.MAX_VEHICLE_NAME_LENGTH));
        if (identity.displayName().equals(displayName)) {
            return false;
        }
        vehicleIdentityByUuid.put(vehicleUuid,
                new VehicleIdentity(identity.typeKey(), identity.number(), displayName));
        setDirty();
        return true;
    }

    private static String normalizeVehicleType(String vehicleTypeKey) {
        String type = vehicleTypeKey.trim().toLowerCase(java.util.Locale.ROOT);
        if (type.equals("chest_boat") || type.equals("chest_raft")) {
            return type;
        }
        if (type.equals("boat_with_chest") || type.endsWith("_boat_with_chest")) {
            return "chest_boat";
        }
        if (type.equals("raft_with_chest") || type.endsWith("_raft_with_chest")) {
            return "chest_raft";
        }
        if (type.endsWith("_boat")) {
            return "boat";
        }
        if (type.endsWith("_raft")) {
            return "raft";
        }
        return type;
    }

    private static String friendlyTypeName(String typeKey) {
        StringBuilder name = new StringBuilder(typeKey.length());
        boolean capitalize = true;
        for (int index = 0; index < typeKey.length(); index++) {
            char character = typeKey.charAt(index);
            if (character == '_' || character == '-') {
                name.append(' ');
                capitalize = true;
            } else {
                name.append(capitalize ? Character.toUpperCase(character) : character);
                capitalize = false;
            }
        }
        return name.toString();
    }

    private static String createDisplayName(String typeKey, int number) {
        String suffix = " " + number;
        String typeName = friendlyTypeName(typeKey);
        int maximumTypeLength = Math.max(1, Constants.Network.MAX_VEHICLE_NAME_LENGTH - suffix.length());
        if (typeName.length() > maximumTypeLength) {
            typeName = typeName.substring(0, maximumTypeLength).stripTrailing();
        }
        return typeName + suffix;
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
        boolean active = assignmentsByAssignee.values().stream()
                .anyMatch(existing -> existing.getRouteId() == routeId && existing.isActive());
        RouteAssignment assignment = new RouteAssignment(routeId, assigneeId, targetWaypointIndex, 1, null, active);
        assignmentsByAssignee.put(assigneeId, assignment);
        setDirty();
        return assignment;
    }

    public boolean removeAssignment(int assigneeId) {
        if (assignmentsByAssignee.remove(assigneeId) == null) {
            return false;
        }
        Set<UUID> removedVehicles = vehicleAssigneeByUuid.entrySet().stream()
                .filter(entry -> entry.getValue() == assigneeId)
                .map((Map.@NonNull Entry<UUID, Integer> entry) -> entry.getKey())
                .collect(java.util.stream.Collectors.toSet());
        vehicleAssigneeByUuid.keySet().removeAll(removedVehicles);
        vehicleLocationByUuid.keySet().removeAll(removedVehicles);
        setDirty();
        return true;
    }

    /** Moves a complete traversal state from one stable assignee to another. */
    public boolean transferAssignment(int sourceAssigneeId, int targetAssigneeId) {
        RouteAssignment source = assignmentsByAssignee.remove(sourceAssigneeId);
        if (source == null) {
            return false;
        }
        assignmentsByAssignee.put(targetAssigneeId, new RouteAssignment(
                source.getRouteId(), targetAssigneeId, source.getTargetWaypointIndex(),
                source.getTraversalDirection(), source.getTraversalTypeOverride(), source.isActive(),
                source.isRestartAnchor()));
        Set<UUID> removedVehicles = vehicleAssigneeByUuid.entrySet().stream()
                .filter(entry -> entry.getValue() == sourceAssigneeId || entry.getValue() == targetAssigneeId)
                .map((Map.@NonNull Entry<UUID, Integer> entry) -> entry.getKey())
                .collect(java.util.stream.Collectors.toSet());
        vehicleAssigneeByUuid.keySet().removeAll(removedVehicles);
        vehicleLocationByUuid.keySet().removeAll(removedVehicles);
        setDirty();
        return true;
    }

    public boolean setAssignmentActive(int assigneeId, boolean active) {
        RouteAssignment assignment = getAssignment(assigneeId);
        if (assignment == null || assignment.isActive() == active) {
            return false;
        }
        boolean restartAnchor = assignment.isRestartAnchor()
                || active && isLegacyOneWayEndpoint(assignment);
        assignmentsByAssignee.put(assigneeId, new RouteAssignment(
                assignment.getRouteId(), assignment.getAssigneeId(), assignment.getTargetWaypointIndex(),
                assignment.getTraversalDirection(), assignment.getTraversalTypeOverride(), active,
                restartAnchor));
        setDirty();
        return true;
    }

    /** Recognizes completed endpoint state saved before explicit restart anchors existed. */
    private boolean isLegacyOneWayEndpoint(RouteAssignment assignment) {
        Route route = getRoute(assignment.getRouteId());
        if (route == null || assignment.getTraversalType(route) != TraversalType.ONE_WAY
                || route.getWaypoints().size() <= 1) {
            return false;
        }
        int target = assignment.getTargetWaypointIndex();
        return target == 0 && assignment.getTraversalDirection() < 0
                || target == route.getWaypoints().size() - 1 && assignment.getTraversalDirection() > 0;
    }

    /** Sets the active state of every assignee using one route. */
    public boolean setRouteAssignmentsActive(int routeId, boolean active) {
        boolean changed = false;
        for (Map.Entry<Integer, RouteAssignment> entry : List.copyOf(assignmentsByAssignee.entrySet())) {
            if (entry.getValue().getRouteId() == routeId) {
                changed |= setAssignmentActive(entry.getKey(), active);
            }
        }
        return changed;
    }

    public boolean updateAssignmentProgress(int assigneeId, int targetWaypointIndex, int traversalDirection) {
        RouteAssignment assignment = getAssignment(assigneeId);
        if (assignment == null) {
            return false;
        }
        return replaceAssignmentTraversalState(
                assignment, targetWaypointIndex, traversalDirection, assignment.isActive(), false);
    }

    /** Stops an assignment while preparing its target and direction for a later restart. */
    public boolean stopAssignmentAtWaypoint(int assigneeId, int targetWaypointIndex, int traversalDirection) {
        RouteAssignment assignment = getAssignment(assigneeId);
        if (assignment == null) {
            return false;
        }
        return replaceAssignmentTraversalState(assignment, targetWaypointIndex, traversalDirection, false, true);
    }

    private boolean replaceAssignmentTraversalState(
            RouteAssignment assignment, int targetWaypointIndex, int traversalDirection, boolean active,
            boolean restartAnchor) {
        assignmentsByAssignee.put(assignment.getAssigneeId(), new RouteAssignment(
                assignment.getRouteId(), assignment.getAssigneeId(), targetWaypointIndex,
                traversalDirection, assignment.getTraversalTypeOverride(), active, restartAnchor));
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
                assignment.getTraversalDirection(), traversalTypeOverride, assignment.isActive(),
                assignment.isRestartAnchor()));
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
        waypoints.add(waypoint.withId(nextWaypointId++));
        replaceRoute(route, new Route(route.getId(), route.getName(), route.getTraversalType(), waypoints));
        setDirty();
        return true;
    }

    public Waypoint getWaypoint(int routeId, int waypointId) {
        Route route = getRoute(routeId);
        return route == null ? null : route.getWaypoints().stream()
                .filter(waypoint -> waypoint.id() == waypointId)
                .findFirst().orElse(null);
    }

    /** Replaces waypoint values while retaining the server-allocated stable ID. */
    public boolean replaceWaypoint(int routeId, int waypointId, Waypoint replacement) {
        Route route = getRoute(routeId);
        return route != null && replaceWaypoint(routeId, waypointId, replacement, findWaypointIndex(route, waypointId));
    }

    /** Replaces waypoint values and moves the stable waypoint ID to the requested list position. */
    public boolean replaceWaypoint(int routeId, int waypointId, Waypoint replacement, int targetPosition) {
        Route route = getRoute(routeId);
        if (route == null || replacement == null) {
            return false;
        }
        int waypointIndex = findWaypointIndex(route, waypointId);
        if (waypointIndex < 0 || targetPosition < 0 || targetPosition >= route.getWaypoints().size()) {
            return false;
        }
        Map<Integer, Integer> assignmentTargets = assignmentTargetWaypointIds(route);
        List<Waypoint> waypoints = new ArrayList<>(route.getWaypoints());
        waypoints.remove(waypointIndex);
        waypoints.add(targetPosition, new Waypoint(
                waypointId, replacement.position(), replacement.dimension(), replacement.action(), replacement.arrivalRadius()));
        replaceRoute(route, new Route(route.getId(), route.getName(), route.getTraversalType(), waypoints));
        remapAssignmentTargets(routeId, waypoints, assignmentTargets);
        setDirty();
        return true;
    }

    private Map<Integer, Integer> assignmentTargetWaypointIds(Route route) {
        Map<Integer, Integer> targets = new HashMap<>();
        for (Map.Entry<Integer, RouteAssignment> entry : assignmentsByAssignee.entrySet()) {
            RouteAssignment assignment = entry.getValue();
            int targetIndex = assignment.getTargetWaypointIndex();
            if (assignment.getRouteId() == route.getId()
                    && targetIndex >= 0 && targetIndex < route.getWaypoints().size()) {
                targets.put(entry.getKey(), route.getWaypoints().get(targetIndex).id());
            }
        }
        return targets;
    }

    private void remapAssignmentTargets(int routeId, List<Waypoint> waypoints, Map<Integer, Integer> targets) {
        for (Map.Entry<Integer, Integer> entry : targets.entrySet()) {
            RouteAssignment assignment = assignmentsByAssignee.get(entry.getKey());
            int targetIndex = findWaypointIndex(waypoints, entry.getValue());
            if (assignment != null && assignment.getRouteId() == routeId && targetIndex >= 0) {
                assignmentsByAssignee.put(entry.getKey(), new RouteAssignment(
                        assignment.getRouteId(), assignment.getAssigneeId(), targetIndex,
                        assignment.getTraversalDirection(), assignment.getTraversalTypeOverride(), assignment.isActive(),
                        assignment.isRestartAnchor()));
            }
        }
    }

    /** Converts behavior without allowing the request to move the waypoint. */
    public boolean convertWaypoint(int routeId, int waypointId,
            com.lastcallsoftware.farandwide.route.WaypointAction action) {
        Waypoint waypoint = getWaypoint(routeId, waypointId);
        return waypoint != null && replaceWaypoint(routeId, waypointId,
                new Waypoint(waypointId, waypoint.position(), waypoint.dimension(), action, waypoint.arrivalRadius()));
    }

    public boolean removeWaypointById(int routeId, int waypointId) {
        Route route = getRoute(routeId);
        return route != null && removeWaypoint(routeId, findWaypointIndex(route, waypointId));
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

    public boolean deleteRoute(int routeId) {
        boolean removed = routes.removeIf(route -> route.getId() == routeId);
        if (!removed) {
            return false;
        }
        Set<Integer> removedAssigneeIds = assignmentsByAssignee.entrySet().stream()
                .filter(entry -> entry.getValue().getRouteId() == routeId)
                .map((Map.@NonNull Entry<Integer, RouteAssignment> entry) -> entry.getKey())
                .collect(java.util.stream.Collectors.toSet());
        assignmentsByAssignee.keySet().removeAll(removedAssigneeIds);
        Set<UUID> removedVehicles = vehicleAssigneeByUuid.entrySet().stream()
                .filter(entry -> removedAssigneeIds.contains(entry.getValue()))
                .map((Map.@NonNull Entry<UUID, Integer> entry) -> entry.getKey())
                .collect(java.util.stream.Collectors.toSet());
        vehicleAssigneeByUuid.keySet().removeAll(removedVehicles);
        vehicleLocationByUuid.keySet().removeAll(removedVehicles);
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

    private static int findWaypointIndex(Route route, int waypointId) {
        return findWaypointIndex(route.getWaypoints(), waypointId);
    }

    private static int findWaypointIndex(List<Waypoint> waypoints, int waypointId) {
        for (int index = 0; index < waypoints.size(); index++) {
            if (waypoints.get(index).id() == waypointId) {
                return index;
            }
        }
        return -1;
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
            int savedNextWaypointId,
            List<Route> routes, Map<Integer, RouteAssignment> assignments, Map<Integer, Integer> selectedRoutes,
            Map<UUID, Integer> vehicleAssignees, Map<UUID, VehicleIdentity> vehicleIdentities,
            Map<UUID, VehicleLocation> vehicleLocations) {
        /*
         * Repair records independently instead of rejecting the complete save.
         * A damaged assignment must not destroy unrelated valid routes. Repairs
         * set the dirty flag so the corrected representation replaces the bad
         * one during the next normal world save.
         */
        FarAndWideSavedData data = new FarAndWideSavedData();
        boolean repaired = false;
        Set<Integer> routeIds = new HashSet<>();
        Set<Integer> waypointIds = new HashSet<>();
        int highestPersistedWaypointId = routes.stream()
                .flatMap(route -> route.getWaypoints().stream())
                .mapToInt((@NonNull Waypoint waypoint) -> waypoint.id())
                .max().orElse(0);
        data.nextWaypointId = Math.max(savedNextWaypointId, highestPersistedWaypointId + 1);
        for (Route route : routes) {
            if (route.getId() <= 0 || !routeIds.add(route.getId())) {
                FarAndWide.LOGGER.warn("Skipping route with invalid or duplicate ID {}", route.getId());
                repaired = true;
                continue;
            }
            List<Waypoint> restoredWaypoints = new ArrayList<>(route.getWaypoints().size());
            for (Waypoint waypoint : route.getWaypoints()) {
                if (waypoint.id() <= 0 || !waypointIds.add(waypoint.id())) {
                    int waypointId = data.nextWaypointId++;
                    restoredWaypoints.add(new Waypoint(
                            waypointId, waypoint.position(), waypoint.dimension(), WaypointAction.normal()));
                    waypointIds.add(waypointId);
                    repaired = true;
                } else {
                    restoredWaypoints.add(waypoint);
                }
            }
            data.routes.add(new Route(
                    route.getId(), route.getName(), route.getTraversalType(), restoredWaypoints));
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
                    assignment.getTraversalTypeOverride(), assignment.isActive(), assignment.isRestartAnchor()));
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

        vehicleAssignees.forEach((vehicleUuid, assigneeId) -> {
            if (vehicleUuid != null && data.getAssignment(assigneeId) != null
                    && !data.vehicleAssigneeByUuid.containsValue(assigneeId)) {
                data.vehicleAssigneeByUuid.put(vehicleUuid, assigneeId);
            } else {
                FarAndWide.LOGGER.warn("Skipping stale or duplicate vehicle association for assignee {}", assigneeId);
            }
        });
        if (data.vehicleAssigneeByUuid.size() != vehicleAssignees.size()) {
            repaired = true;
        }

        for (Map.Entry<UUID, VehicleIdentity> entry : vehicleIdentities.entrySet()) {
            UUID vehicleUuid = entry.getKey();
            VehicleIdentity identity = entry.getValue();
            if (vehicleUuid != null && identity != null && !identity.typeKey().isBlank()
                    && identity.number() > 0 && !identity.displayName().isBlank()
                    && identity.displayName().length() <= Constants.Network.MAX_VEHICLE_NAME_LENGTH) {
                data.vehicleIdentityByUuid.put(vehicleUuid, identity);
            } else {
                repaired = true;
            }
        }

        for (Map.Entry<UUID, VehicleLocation> entry : vehicleLocations.entrySet()) {
            UUID vehicleUuid = entry.getKey();
            VehicleLocation location = entry.getValue();
            if (vehicleUuid != null && location != null && location.dimension() != null
                    && location.position() != null && data.vehicleAssigneeByUuid.containsKey(vehicleUuid)) {
                data.vehicleLocationByUuid.put(vehicleUuid, location);
            } else {
                repaired = true;
            }
        }

        int highestRouteId = data.routes.stream().mapToInt((@NonNull Route route) -> route.getId()).max().orElse(0);
        // Include rejected records when finding the allocator floor. Their IDs
        // may still exist on entity attachments, so reusing one would alias two
        // logically different assignees.
        int highestAssignmentId = assignments.keySet().stream().mapToInt((@NonNull Integer id) -> id.intValue()).max().orElse(0);
        int highestSelectionId = selectedRoutes.keySet().stream().mapToInt((@NonNull Integer id) -> id.intValue()).max().orElse(0);
        int highestAssigneeId = Math.max(highestAssignmentId, highestSelectionId);
        data.nextRouteId = Math.max(savedNextRouteId, highestRouteId + 1);
        data.nextAssigneeId = Math.max(savedNextAssigneeId, highestAssigneeId + 1);
        if (data.nextRouteId != savedNextRouteId || data.nextAssigneeId != savedNextAssigneeId
                || data.nextWaypointId != savedNextWaypointId) {
            FarAndWide.LOGGER.warn("Repairing stale route, assignee, or waypoint ID allocator");
            repaired = true;
        }
        if (dataVersion > Constants.Persistence.CURRENT_DATA_VERSION) {
            FarAndWide.LOGGER.warn("Route data version {} is newer than supported version {}", dataVersion,
                    Constants.Persistence.CURRENT_DATA_VERSION);
        }
        if (repaired) {
            data.setDirty();
        }
        return data;
    }

    /** Persistent human-readable identity retained even while a vehicle is unassigned. */
    public record VehicleIdentity(String typeKey, int number, String displayName) {
    }

    /** Last known location used to reacquire an unloaded assigned vehicle. */
    public record VehicleLocation(Identifier dimension, BlockPos position) {
    }
}
