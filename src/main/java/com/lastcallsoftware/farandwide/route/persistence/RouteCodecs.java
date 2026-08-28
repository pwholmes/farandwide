package com.lastcallsoftware.farandwide.route.persistence;

import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.route.CargoBehavior;
import com.lastcallsoftware.farandwide.route.CargoFilter;
import com.lastcallsoftware.farandwide.route.CargoOperation;
import com.lastcallsoftware.farandwide.route.CargoStationBinding;
import com.lastcallsoftware.farandwide.route.TraversalType;
import com.lastcallsoftware.farandwide.route.Waypoint;
import com.lastcallsoftware.farandwide.route.WaypointAction;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
    private static final Codec<CargoOperation> CARGO_OPERATION = enumCodec(CargoOperation.class);
    private static final Codec<CargoFilter.Mode> CARGO_FILTER_MODE = enumCodec(CargoFilter.Mode.class);
    private static final Codec<Direction> DIRECTION = enumCodec(Direction.class);
    private static final Codec<CargoFilter> CARGO_FILTER = RecordCodecBuilder.create(instance -> instance.group(
            CARGO_FILTER_MODE.fieldOf("mode").forGetter(CargoFilter::mode),
            Identifier.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(CargoFilter::itemIds))
            .apply(instance, CargoFilter::new));
    private static final Codec<CargoStationBinding> CARGO_STATION = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("x").forGetter(binding -> binding.position().getX()),
            Codec.INT.fieldOf("y").forGetter(binding -> binding.position().getY()),
            Codec.INT.fieldOf("z").forGetter(binding -> binding.position().getZ()),
            DIRECTION.fieldOf("side").forGetter(CargoStationBinding::accessSide))
            .apply(instance, (x, y, z, side) -> new CargoStationBinding(new BlockPos(x, y, z), side)));
    private static final Codec<CargoBehavior> CARGO_BEHAVIOR = RecordCodecBuilder.create(instance -> instance.group(
            CARGO_OPERATION.fieldOf("operation").forGetter(CargoBehavior::operation),
            CARGO_FILTER.fieldOf("loadFilter").forGetter(CargoBehavior::loadFilter),
            CARGO_FILTER.fieldOf("unloadFilter").forGetter(CargoBehavior::unloadFilter),
            CARGO_STATION.optionalFieldOf("loadStation").forGetter(CargoBehavior::loadStation),
            CARGO_STATION.optionalFieldOf("unloadStation").forGetter(CargoBehavior::unloadStation),
            CARGO_STATION.optionalFieldOf("station").forGetter(behavior -> Optional.empty()))
            .apply(instance, RouteCodecs::cargoBehavior));
    private static final Codec<Waypoint> WAYPOINT = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("id", 0).forGetter(Waypoint::id),
            Codec.DOUBLE.fieldOf("x").forGetter(waypoint -> waypoint.position().x),
            Codec.DOUBLE.fieldOf("y").forGetter(waypoint -> waypoint.position().y),
            Codec.DOUBLE.fieldOf("z").forGetter(waypoint -> waypoint.position().z),
            Identifier.CODEC.optionalFieldOf("dimension", Waypoint.DEFAULT_DIMENSION).forGetter(Waypoint::dimension),
            Codec.DOUBLE.optionalFieldOf("arrivalRadius", Constants.Waypoints.DEFAULT_ARRIVAL_RADIUS)
                    .forGetter(Waypoint::arrivalRadius),
            CARGO_BEHAVIOR.optionalFieldOf("cargo").forGetter(RouteCodecs::cargoBehavior))
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
            Codec.INT.optionalFieldOf("dataVersion", Constants.Persistence.CURRENT_DATA_VERSION)
                    .forGetter(data -> Constants.Persistence.CURRENT_DATA_VERSION),
            Codec.INT.optionalFieldOf("nextRouteId", 1).forGetter(FarAndWideSavedData::getNextRouteId),
            Codec.INT.optionalFieldOf("nextAssigneeId", 1).forGetter(FarAndWideSavedData::getNextAssigneeId),
            Codec.INT.optionalFieldOf("nextWaypointId", 1).forGetter(FarAndWideSavedData::getNextWaypointId),
            ROUTE.listOf().optionalFieldOf("routes", List.of()).forGetter(FarAndWideSavedData::getRoutes),
            ASSIGNMENT_ENTRY.listOf().optionalFieldOf("assignments", List.of())
                    .forGetter(RouteCodecs::assignmentEntries),
            SELECTED_ROUTE_ENTRY.listOf().optionalFieldOf("selectedRoutes", List.of())
                    .forGetter(RouteCodecs::selectedRouteEntries))
            .apply(instance, RouteCodecs::savedData));

    private RouteCodecs() {
    }

    private static Waypoint waypoint(int id, double x, double y, double z, Identifier dimension, double arrivalRadius,
            Optional<CargoBehavior> cargoBehavior) {
        WaypointAction action = cargoBehavior.<WaypointAction>map(WaypointAction::cargo)
                .orElseGet(WaypointAction::normal);
        return new Waypoint(id, new Vec3(x, y, z), dimension, action, arrivalRadius);
    }

    private static Optional<CargoBehavior> cargoBehavior(Waypoint waypoint) {
        return waypoint.action() instanceof WaypointAction.Cargo cargo
                ? Optional.of(cargo.behavior())
                : Optional.empty();
    }

    private static CargoBehavior cargoBehavior(CargoOperation operation, CargoFilter loadFilter,
            CargoFilter unloadFilter, Optional<CargoStationBinding> loadStation,
            Optional<CargoStationBinding> unloadStation, Optional<CargoStationBinding> legacyStation) {
        return new CargoBehavior(operation, loadFilter, unloadFilter,
                loadStation.or(() -> legacyStation), unloadStation.or(() -> legacyStation));
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
            int nextWaypointId,
            List<Route> routes, List<AssignmentEntry> assignments, List<SelectedRouteEntry> selectedRoutes) {
        Map<Integer, RouteAssignment> assignmentsByAssignee = assignments.stream().collect(Collectors.toMap(
                AssignmentEntry::assigneeId, AssignmentEntry::assignment, (first, ignored) -> first));
        Map<Integer, Integer> selectedRouteByAssignee = selectedRoutes.stream().collect(Collectors.toMap(
                SelectedRouteEntry::assigneeId, SelectedRouteEntry::routeId, (first, ignored) -> first));
        return FarAndWideSavedData.restore(dataVersion, nextRouteId, nextAssigneeId, nextWaypointId, routes,
                assignmentsByAssignee, selectedRouteByAssignee);
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> enumClass) {
        return Codec.STRING.xmap(
                value -> Enum.valueOf(enumClass, value.toUpperCase(java.util.Locale.ROOT)),
                value -> value.name().toLowerCase(java.util.Locale.ROOT));
    }

    private record AssignmentEntry(int assigneeId, RouteAssignment assignment) {
    }

    private record SelectedRouteEntry(int assigneeId, int routeId) {
    }
}
