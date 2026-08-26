package com.lastcallsoftware.farandwide.route.persistence;

import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.route.TraversalType;
import com.lastcallsoftware.farandwide.route.Waypoint;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Defines the on-disk format for all route persistence.
 *
 * <p>These codecs are intentionally separate from {@link FarAndWideSavedData}:
 * this file answers "how is it encoded?", while saved data answers "how is it
 * stored and changed?" Field names are part of the save compatibility contract.
 * When adding a field, use an explicit default if old worlds can safely omit it;
 * otherwise add migration logic and increment the saved-data version.
 *
 * <p>Codec construction creates immutable domain records. Logical validation
 * that requires relationships between records—such as an assignment's route
 * existing—is performed by {@link FarAndWideSavedData#restore} after decoding.
 */
public final class RouteCodecs {
    private static final Codec<Waypoint> WAYPOINT = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("x").forGetter(waypoint -> waypoint.position().x),
            Codec.DOUBLE.fieldOf("y").forGetter(waypoint -> waypoint.position().y),
            Codec.DOUBLE.fieldOf("z").forGetter(waypoint -> waypoint.position().z),
            Identifier.CODEC.optionalFieldOf("dimension", Waypoint.DEFAULT_DIMENSION).forGetter(Waypoint::dimension))
            .apply(instance, RouteCodecs::waypoint));

    private static final Codec<Route> ROUTE = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("id").forGetter(Route::getId),
            Codec.STRING.fieldOf("name").forGetter(Route::getName),
            TraversalType.CODEC.fieldOf("traversalType").forGetter(Route::getTraversalType),
            WAYPOINT.listOf().fieldOf("waypoints").forGetter(Route::getWaypoints))
            .apply(instance, RouteCodecs::route));

    private static final Codec<RouteAssignment> ASSIGNMENT = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("routeId").forGetter(RouteAssignment::getRouteId),
            Codec.INT.fieldOf("assigneeId").forGetter(RouteAssignment::getAssigneeId),
            Codec.INT.fieldOf("targetWaypointIndex").forGetter(RouteAssignment::getTargetWaypointIndex),
            Codec.INT.fieldOf("traversalDirection").forGetter(RouteAssignment::getTraversalDirection),
            TraversalType.CODEC.optionalFieldOf("traversalTypeOverride")
                    .forGetter(assignment -> Optional.ofNullable(assignment.getTraversalTypeOverride())),
            Codec.BOOL.fieldOf("active").forGetter(RouteAssignment::isActive))
            .apply(instance, RouteCodecs::assignment));

    private static final Codec<AssignmentEntry> ASSIGNMENT_ENTRY = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("assigneeId").forGetter(AssignmentEntry::assigneeId),
            ASSIGNMENT.fieldOf("assignment").forGetter(AssignmentEntry::assignment))
            .apply(instance, AssignmentEntry::new));

    private static final Codec<SelectedRouteEntry> SELECTED_ROUTE_ENTRY = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("assigneeId").forGetter(SelectedRouteEntry::assigneeId),
            Codec.INT.fieldOf("routeId").forGetter(SelectedRouteEntry::routeId))
            .apply(instance, SelectedRouteEntry::new));

    /** Root codec supplied to Minecraft's {@code SavedDataType}. */
    static final Codec<FarAndWideSavedData> SAVED_DATA = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("dataVersion", FarAndWideSavedData.CURRENT_DATA_VERSION)
                    .forGetter(data -> FarAndWideSavedData.CURRENT_DATA_VERSION),
            Codec.INT.optionalFieldOf("nextRouteId", 1).forGetter(FarAndWideSavedData::getNextRouteId),
            Codec.INT.optionalFieldOf("nextAssigneeId", 1).forGetter(FarAndWideSavedData::getNextAssigneeId),
            ROUTE.listOf().optionalFieldOf("routes", List.of()).forGetter(FarAndWideSavedData::getRoutes),
            ASSIGNMENT_ENTRY.listOf().optionalFieldOf("assignments", List.of())
                    .forGetter(RouteCodecs::assignmentEntries),
            SELECTED_ROUTE_ENTRY.listOf().optionalFieldOf("selectedRoutes", List.of())
                    .forGetter(RouteCodecs::selectedRouteEntries))
            .apply(instance, RouteCodecs::savedData));

    private RouteCodecs() {
    }

    private static Waypoint waypoint(double x, double y, double z, Identifier dimension) {
        return new Waypoint(new Vec3(x, y, z), dimension);
    }

    private static Route route(int id, String name, TraversalType traversalType, List<Waypoint> waypoints) {
        return new Route(id, name, traversalType, waypoints);
    }

    private static RouteAssignment assignment(int routeId, int assigneeId, int targetWaypointIndex,
            int traversalDirection, Optional<TraversalType> traversalTypeOverride, boolean active) {
        return new RouteAssignment(routeId, assigneeId, targetWaypointIndex, traversalDirection,
                traversalTypeOverride.orElse(null), active);
    }

    private static List<AssignmentEntry> assignmentEntries(FarAndWideSavedData data) {
        return data.getAssignmentsByAssignee().entrySet().stream()
                .map(entry -> new AssignmentEntry(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static List<SelectedRouteEntry> selectedRouteEntries(FarAndWideSavedData data) {
        return data.getSelectedRoutesByAssignee().entrySet().stream()
                .map(entry -> new SelectedRouteEntry(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static FarAndWideSavedData savedData(int dataVersion, int nextRouteId, int nextAssigneeId,
            List<Route> routes, List<AssignmentEntry> assignments, List<SelectedRouteEntry> selectedRoutes) {
        Map<Integer, RouteAssignment> assignmentsByAssignee = assignments.stream().collect(Collectors.toMap(
                AssignmentEntry::assigneeId, AssignmentEntry::assignment, (first, ignored) -> first));
        Map<Integer, Integer> selectedRouteByAssignee = selectedRoutes.stream().collect(Collectors.toMap(
                SelectedRouteEntry::assigneeId, SelectedRouteEntry::routeId, (first, ignored) -> first));
        return FarAndWideSavedData.restore(dataVersion, nextRouteId, nextAssigneeId, routes,
                assignmentsByAssignee, selectedRouteByAssignee);
    }

    private record AssignmentEntry(int assigneeId, RouteAssignment assignment) {
    }

    private record SelectedRouteEntry(int assigneeId, int routeId) {
    }
}
