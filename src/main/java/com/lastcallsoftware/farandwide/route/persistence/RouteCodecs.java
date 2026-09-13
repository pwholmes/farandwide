package com.lastcallsoftware.farandwide.route.persistence;

import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.route.CargoBehavior;
import com.lastcallsoftware.farandwide.route.CargoFilter;
import com.lastcallsoftware.farandwide.route.CargoOperation;
import com.lastcallsoftware.farandwide.route.CargoStationBinding;
import com.lastcallsoftware.farandwide.route.CargoOrder;
import com.lastcallsoftware.farandwide.route.OrderLeg;
import com.lastcallsoftware.farandwide.route.OrderLine;
import com.lastcallsoftware.farandwide.route.RouteOperationResult;
import com.lastcallsoftware.farandwide.route.TraversalType;
import com.lastcallsoftware.farandwide.route.Waypoint;
import com.lastcallsoftware.farandwide.route.WaypointAction;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.transfer.item.ItemResource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.phys.Vec3;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;

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
@NonNullByDefault
public final class RouteCodecs {
    private static final Codec<CargoOperation> CARGO_OPERATION = enumCodec(CargoOperation.class);
    private static final Codec<CargoFilter.Mode> CARGO_FILTER_MODE = enumCodec(CargoFilter.Mode.class);
    private static final Codec<Direction> DIRECTION = enumCodec(Direction.class);
    private static final Codec<CargoFilter> CARGO_FILTER = RecordCodecBuilder.create(instance -> instance.group(
            CARGO_FILTER_MODE.fieldOf("mode").forGetter((@NonNull CargoFilter filter) -> filter.mode()),
            Identifier.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter((@NonNull CargoFilter filter) -> filter.itemIds()))
            .apply(instance, (CargoFilter.@NonNull Mode mode, @NonNull List<Identifier> itemIds) -> new CargoFilter(mode, itemIds)));
    private static final Codec<CargoStationBinding> CARGO_STATION = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("x").forGetter(binding -> binding.position().getX()),
            Codec.INT.fieldOf("y").forGetter(binding -> binding.position().getY()),
            Codec.INT.fieldOf("z").forGetter(binding -> binding.position().getZ()),
            DIRECTION.fieldOf("side").forGetter((@NonNull CargoStationBinding binding) -> binding.accessSide()))
            .apply(instance, (@NonNull Integer x, @NonNull Integer y, @NonNull Integer z, @NonNull Direction side) -> new CargoStationBinding(new BlockPos(x, y, z), side)));
    private static final Codec<CargoBehavior> CARGO_BEHAVIOR = RecordCodecBuilder.create(instance -> instance.group(
            CARGO_OPERATION.fieldOf("operation").forGetter((@NonNull CargoBehavior behavior) -> behavior.operation()),
            CARGO_FILTER.fieldOf("loadFilter").forGetter((@NonNull CargoBehavior behavior) -> behavior.loadFilter()),
            CARGO_FILTER.fieldOf("unloadFilter").forGetter((@NonNull CargoBehavior behavior) -> behavior.unloadFilter()),
            CARGO_STATION.optionalFieldOf("loadStation").forGetter((@NonNull CargoBehavior behavior) -> behavior.loadStation()),
            CARGO_STATION.optionalFieldOf("unloadStation").forGetter((@NonNull CargoBehavior behavior) -> behavior.unloadStation()),
            CARGO_STATION.optionalFieldOf("station").forGetter((@NonNull CargoBehavior behavior) -> Optional.empty()),
            CARGO_STATION.listOf().optionalFieldOf("sourceInventories", List.of())
                    .forGetter((@NonNull CargoBehavior behavior) -> behavior.sourceInventories()))
            .apply(instance, (@NonNull CargoOperation operation, @NonNull CargoFilter loadFilter,
                    @NonNull CargoFilter unloadFilter, @NonNull Optional<CargoStationBinding> loadStation,
                    @NonNull Optional<CargoStationBinding> unloadStation, @NonNull Optional<CargoStationBinding> legacyStation,
                    @NonNull List<CargoStationBinding> sources)
                    -> cargoBehavior(operation, loadFilter, unloadFilter, loadStation, unloadStation, legacyStation, sources)));
    private static final Codec<Waypoint> WAYPOINT = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("id", 0).forGetter((@NonNull Waypoint waypoint) -> waypoint.id()),
            Codec.DOUBLE.fieldOf("x").forGetter((@NonNull Waypoint waypoint) -> waypoint.position().x),
            Codec.DOUBLE.fieldOf("y").forGetter((@NonNull Waypoint waypoint) -> waypoint.position().y),
            Codec.DOUBLE.fieldOf("z").forGetter((@NonNull Waypoint waypoint) -> waypoint.position().z),
            Identifier.CODEC.optionalFieldOf("dimension", Waypoint.DEFAULT_DIMENSION).forGetter((@NonNull Waypoint waypoint) -> waypoint.dimension()),
            Codec.DOUBLE.optionalFieldOf("arrivalRadius", Constants.Waypoints.DEFAULT_ARRIVAL_RADIUS)
                    .forGetter((@NonNull Waypoint waypoint) -> waypoint.arrivalRadius()),
            CARGO_BEHAVIOR.optionalFieldOf("cargo").forGetter((@NonNull Waypoint waypoint) -> cargoBehavior(waypoint)))
            .apply(instance, (@NonNull Integer id, @NonNull Double x, @NonNull Double y, @NonNull Double z,
                    @NonNull Identifier dimension, @NonNull Double arrivalRadius,
                    @NonNull Optional<CargoBehavior> cargoBehavior)
                    -> waypoint(id, x, y, z, dimension, arrivalRadius, cargoBehavior)));

    private static final Codec<Route> ROUTE = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("id").forGetter((@NonNull Route route) -> route.getId()),
            Codec.STRING.fieldOf("name").forGetter((@NonNull Route route) -> route.getName()),
            TraversalType.CODEC.fieldOf("traversalType").forGetter((@NonNull Route route) -> route.getTraversalType()),
            WAYPOINT.listOf().fieldOf("waypoints").forGetter((@NonNull Route route) -> route.getWaypoints()))
            .apply(instance, (@NonNull Integer id, @NonNull String name, @NonNull TraversalType traversalType,
                    @NonNull List<Waypoint> waypoints) -> route(id, name, traversalType, waypoints)));

    private static final Codec<RouteAssignment> ASSIGNMENT = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("routeId").forGetter((@NonNull RouteAssignment assignment) -> assignment.getRouteId()),
            Codec.INT.fieldOf("assigneeId").forGetter((@NonNull RouteAssignment assignment) -> assignment.getAssigneeId()),
            Codec.INT.fieldOf("targetWaypointIndex").forGetter((@NonNull RouteAssignment assignment) -> assignment.getTargetWaypointIndex()),
            Codec.INT.fieldOf("traversalDirection").forGetter((@NonNull RouteAssignment assignment) -> assignment.getTraversalDirection()),
            TraversalType.CODEC.optionalFieldOf("traversalTypeOverride")
                    .forGetter(assignment -> Optional.ofNullable(assignment.getTraversalTypeOverride())),
            Codec.BOOL.fieldOf("active").forGetter((@NonNull RouteAssignment assignment) -> assignment.isActive()),
            Codec.BOOL.optionalFieldOf("restartAnchor", false)
                    .forGetter((@NonNull RouteAssignment assignment) -> assignment.isRestartAnchor()))
            .apply(instance, (@NonNull Integer routeId, @NonNull Integer assigneeId,
                    @NonNull Integer targetWaypointIndex, @NonNull Integer traversalDirection,
                    @NonNull Optional<TraversalType> traversalTypeOverride, @NonNull Boolean active,
                    @NonNull Boolean restartAnchor)
                    -> assignment(routeId, assigneeId, targetWaypointIndex, traversalDirection,
                            traversalTypeOverride, active, restartAnchor)));

    private static final Codec<AssignmentEntry> ASSIGNMENT_ENTRY = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("assigneeId").forGetter((@NonNull AssignmentEntry entry) -> entry.assigneeId()),
            ASSIGNMENT.fieldOf("assignment").forGetter((@NonNull AssignmentEntry entry) -> entry.assignment()))
            .apply(instance, (@NonNull Integer assigneeId, @NonNull RouteAssignment assignment)
                    -> new AssignmentEntry(assigneeId, assignment)));

    private static final Codec<SelectedRouteEntry> SELECTED_ROUTE_ENTRY = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("assigneeId").forGetter((@NonNull SelectedRouteEntry entry) -> entry.assigneeId()),
            Codec.INT.fieldOf("routeId").forGetter((@NonNull SelectedRouteEntry entry) -> entry.routeId()))
            .apply(instance, (@NonNull Integer assigneeId, @NonNull Integer routeId)
                    -> new SelectedRouteEntry(assigneeId, routeId)));

    private static final Codec<VehicleAssigneeEntry> VEHICLE_ASSIGNEE_ENTRY = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("vehicleUuid").forGetter((@NonNull VehicleAssigneeEntry entry) -> entry.vehicleUuid()),
            Codec.INT.fieldOf("assigneeId").forGetter((@NonNull VehicleAssigneeEntry entry) -> entry.assigneeId()))
            .apply(instance, (@NonNull UUID vehicleUuid, @NonNull Integer assigneeId)
                    -> new VehicleAssigneeEntry(vehicleUuid, assigneeId)));

    private static final Codec<VehicleIdentityEntry> VEHICLE_IDENTITY_ENTRY = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("vehicleUuid").forGetter((@NonNull VehicleIdentityEntry entry) -> entry.vehicleUuid()),
            Codec.STRING.fieldOf("typeKey").forGetter((@NonNull VehicleIdentityEntry entry) -> entry.identity().typeKey()),
            Codec.INT.fieldOf("number").forGetter((@NonNull VehicleIdentityEntry entry) -> entry.identity().number()),
            Codec.STRING.fieldOf("displayName").forGetter((@NonNull VehicleIdentityEntry entry) -> entry.identity().displayName()),
            Codec.BOOL.optionalFieldOf("cargoCapable", false)
                    .forGetter((@NonNull VehicleIdentityEntry entry) -> entry.identity().cargoCapable()))
            .apply(instance, (@NonNull UUID vehicleUuid, @NonNull String typeKey, @NonNull Integer number,
                    @NonNull String displayName, @NonNull Boolean cargoCapable) -> new VehicleIdentityEntry(vehicleUuid,
                            new FarAndWideSavedData.VehicleIdentity(typeKey, number, displayName, cargoCapable))));

    private static final Codec<VehicleLocationEntry> VEHICLE_LOCATION_ENTRY = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("vehicleUuid").forGetter((@NonNull VehicleLocationEntry entry) -> entry.vehicleUuid()),
            Identifier.CODEC.fieldOf("dimension").forGetter((@NonNull VehicleLocationEntry entry) -> entry.location().dimension()),
            Codec.INT.fieldOf("x").forGetter((@NonNull VehicleLocationEntry entry) -> entry.location().position().getX()),
            Codec.INT.fieldOf("y").forGetter((@NonNull VehicleLocationEntry entry) -> entry.location().position().getY()),
            Codec.INT.fieldOf("z").forGetter((@NonNull VehicleLocationEntry entry) -> entry.location().position().getZ()))
            .apply(instance, (@NonNull UUID vehicleUuid, @NonNull Identifier dimension,
                    @NonNull Integer x, @NonNull Integer y, @NonNull Integer z)
                    -> new VehicleLocationEntry(vehicleUuid,
                            new FarAndWideSavedData.VehicleLocation(dimension, new BlockPos(x, y, z)))));

    private static final Codec<DeathRouteEntry> DEATH_ROUTE_ENTRY = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("playerUuid").forGetter((@NonNull DeathRouteEntry entry) -> entry.playerUuid()),
            Codec.INT.fieldOf("routeId").forGetter((@NonNull DeathRouteEntry entry) -> entry.routeId()))
            .apply(instance, (@NonNull UUID playerUuid, @NonNull Integer routeId)
                    -> new DeathRouteEntry(playerUuid, routeId)));

    private static final Codec<OrderLine> ORDER_LINE_WITH_COMPONENTS = RecordCodecBuilder.create(instance -> instance.group(
            ItemResource.CODEC.fieldOf("resource").forGetter((@NonNull OrderLine line) -> line.resource()),
            Codec.intRange(1, Constants.Orders.MAX_QUANTITY).fieldOf("requested")
                    .forGetter((@NonNull OrderLine line) -> line.requested()),
            Codec.intRange(0, Constants.Orders.MAX_QUANTITY).fieldOf("delivered")
                    .forGetter((@NonNull OrderLine line) -> line.delivered()))
            .apply(instance, (@NonNull ItemResource resource, @NonNull Integer requested, @NonNull Integer delivered)
                    -> new OrderLine(resource, requested, delivered)));
    private static final Codec<OrderLine> LEGACY_ORDER_LINE = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("item").forGetter((@NonNull OrderLine line) -> line.itemId()),
            Codec.intRange(1, Constants.Orders.MAX_QUANTITY).fieldOf("requested")
                    .forGetter((@NonNull OrderLine line) -> line.requested()),
            Codec.intRange(0, Constants.Orders.MAX_QUANTITY).fieldOf("delivered")
                    .forGetter((@NonNull OrderLine line) -> line.delivered()))
            .apply(instance, (@NonNull Identifier item, @NonNull Integer requested, @NonNull Integer delivered)
                    -> new OrderLine(item, requested, delivered)));
    private static final Codec<OrderLine> ORDER_LINE = ORDER_LINE_WITH_COMPONENTS.withAlternative(LEGACY_ORDER_LINE);
    private static final Codec<OrderLeg> ORDER_LEG = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("route").forGetter((@NonNull OrderLeg leg) -> leg.routeId()),
            Codec.INT.fieldOf("origin").forGetter((@NonNull OrderLeg leg) -> leg.originWaypointId()),
            Codec.INT.fieldOf("destination").forGetter((@NonNull OrderLeg leg) -> leg.destinationWaypointId()),
            CARGO_STATION.fieldOf("destinationStation")
                    .forGetter((@NonNull OrderLeg leg) -> leg.destinationStation()),
            ORDER_LINE.listOf().fieldOf("lines").forGetter((@NonNull OrderLeg leg) -> leg.lines()),
            enumCodec(RouteOperationResult.class).optionalFieldOf("activation", RouteOperationResult.SUCCESS)
                    .forGetter((@NonNull OrderLeg leg) -> leg.activationResult()))
            .apply(instance, (@NonNull Integer route, @NonNull Integer origin, @NonNull Integer destination,
                    @NonNull CargoStationBinding destinationStation, @NonNull List<OrderLine> lines,
                    @NonNull RouteOperationResult activation)
                    -> new OrderLeg(route, origin, destination, destinationStation, lines, activation)));
    private static final Codec<CargoOrder> MODERN_ORDER = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter((@NonNull CargoOrder order) -> order.id()),
            UUIDUtil.CODEC.fieldOf("player").forGetter((@NonNull CargoOrder order) -> order.playerId()),
            ORDER_LEG.listOf().fieldOf("legs").forGetter((@NonNull CargoOrder order) -> order.legs()))
            .apply(instance, (@NonNull UUID id, @NonNull UUID player, @NonNull List<OrderLeg> legs)
                    -> new CargoOrder(id, player, legs)));
    private static final Codec<CargoOrder> LEGACY_ORDER = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter((@NonNull CargoOrder order) -> order.id()),
            UUIDUtil.CODEC.fieldOf("player").forGetter((@NonNull CargoOrder order) -> order.playerId()),
            Codec.INT.fieldOf("route").forGetter((@NonNull CargoOrder order) -> order.routeId()),
            Codec.INT.fieldOf("origin").forGetter((@NonNull CargoOrder order) -> order.originWaypointId()),
            Codec.INT.fieldOf("destination").forGetter((@NonNull CargoOrder order) -> order.destinationWaypointId()),
            CARGO_STATION.fieldOf("destinationStation")
                    .forGetter((@NonNull CargoOrder order) -> order.destinationStation()),
            ORDER_LINE.listOf().fieldOf("lines").forGetter((@NonNull CargoOrder order) -> order.lines()),
            enumCodec(RouteOperationResult.class).optionalFieldOf("activation", RouteOperationResult.SUCCESS)
                    .forGetter((@NonNull CargoOrder order) -> order.activationResult()))
            .apply(instance, (@NonNull UUID id, @NonNull UUID player, @NonNull Integer route,
                    @NonNull Integer origin, @NonNull Integer destination,
                    @NonNull CargoStationBinding destinationStation, @NonNull List<OrderLine> lines,
                    @NonNull RouteOperationResult activation)
                    -> new CargoOrder(id, player, route, origin, destination, destinationStation, lines, activation)));
    private static final Codec<CargoOrder> ORDER = MODERN_ORDER.withAlternative(LEGACY_ORDER);

    /** Root codec supplied to Minecraft's {@code SavedDataType}. */
    static final Codec<FarAndWideSavedData> SAVED_DATA = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("dataVersion", Constants.Persistence.CURRENT_DATA_VERSION)
                    .forGetter((@NonNull FarAndWideSavedData data) -> Constants.Persistence.CURRENT_DATA_VERSION),
            Codec.INT.optionalFieldOf("nextRouteId", 1).forGetter((@NonNull FarAndWideSavedData data) -> data.getNextRouteId()),
            Codec.INT.optionalFieldOf("nextAssigneeId", 1).forGetter((@NonNull FarAndWideSavedData data) -> data.getNextAssigneeId()),
            Codec.INT.optionalFieldOf("nextWaypointId", 1).forGetter((@NonNull FarAndWideSavedData data) -> data.getNextWaypointId()),
            ROUTE.listOf().optionalFieldOf("routes", List.of()).forGetter((@NonNull FarAndWideSavedData data) -> data.getRoutes()),
            ASSIGNMENT_ENTRY.listOf().optionalFieldOf("assignments", List.of())
                    .forGetter((@NonNull FarAndWideSavedData data) -> assignmentEntries(data)),
            SELECTED_ROUTE_ENTRY.listOf().optionalFieldOf("selectedRoutes", List.of())
                    .forGetter((@NonNull FarAndWideSavedData data) -> selectedRouteEntries(data)),
            VEHICLE_ASSIGNEE_ENTRY.listOf().optionalFieldOf("vehicleAssignees", List.of())
                    .forGetter((@NonNull FarAndWideSavedData data) -> vehicleAssigneeEntries(data)),
            VEHICLE_IDENTITY_ENTRY.listOf().optionalFieldOf("vehicleIdentities", List.of())
                    .forGetter((@NonNull FarAndWideSavedData data) -> vehicleIdentityEntries(data)),
            VEHICLE_LOCATION_ENTRY.listOf().optionalFieldOf("vehicleLocations", List.of())
                    .forGetter((@NonNull FarAndWideSavedData data) -> vehicleLocationEntries(data)),
            DEATH_ROUTE_ENTRY.listOf().optionalFieldOf("deathRoutes", List.of())
                    .forGetter((@NonNull FarAndWideSavedData data) -> deathRouteEntries(data)),
            ORDER.listOf().optionalFieldOf("orders", List.of())
                    .forGetter((@NonNull FarAndWideSavedData data) -> data.getOrders()))
            .apply(instance, (@NonNull Integer dataVersion, @NonNull Integer nextRouteId,
                    @NonNull Integer nextAssigneeId, @NonNull Integer nextWaypointId,
                    @NonNull List<Route> routes, @NonNull List<AssignmentEntry> assignments,
                    @NonNull List<SelectedRouteEntry> selectedRoutes,
                    @NonNull List<VehicleAssigneeEntry> vehicleAssignees,
                    @NonNull List<VehicleIdentityEntry> vehicleIdentities,
                    @NonNull List<VehicleLocationEntry> vehicleLocations,
                    @NonNull List<DeathRouteEntry> deathRoutes, @NonNull List<CargoOrder> orders)
                    -> savedData(dataVersion, nextRouteId, nextAssigneeId, nextWaypointId, routes,
                            assignments, selectedRoutes, vehicleAssignees, vehicleIdentities, vehicleLocations,
                            deathRoutes, orders)));

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
            Optional<CargoStationBinding> unloadStation, Optional<CargoStationBinding> legacyStation,
            List<CargoStationBinding> sources) {
        return new CargoBehavior(operation, loadFilter, unloadFilter,
                loadStation.or(() -> legacyStation), unloadStation.or(() -> legacyStation), sources);
    }

    private static Route route(int id, String name, TraversalType traversalType, List<Waypoint> waypoints) {
        return new Route(id, name, traversalType, waypoints);
    }

    private static RouteAssignment assignment(int routeId, int assigneeId, int targetWaypointIndex,
            int traversalDirection, Optional<TraversalType> traversalTypeOverride, boolean active,
            boolean restartAnchor) {
        return new RouteAssignment(routeId, assigneeId, targetWaypointIndex, traversalDirection,
                traversalTypeOverride.orElse(null), active, restartAnchor);
    }

    private static List<AssignmentEntry> assignmentEntries(FarAndWideSavedData data) {
        return data.getAssignmentsByAssignee().entrySet().stream()
                .map((Map.@NonNull Entry<Integer, RouteAssignment> entry) -> new AssignmentEntry(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static List<SelectedRouteEntry> selectedRouteEntries(FarAndWideSavedData data) {
        return data.getSelectedRoutesByAssignee().entrySet().stream()
                .map((Map.@NonNull Entry<Integer, Integer> entry) -> new SelectedRouteEntry(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static List<VehicleAssigneeEntry> vehicleAssigneeEntries(FarAndWideSavedData data) {
        return data.getVehicleAssigneesByUuid().entrySet().stream()
                .map((Map.@NonNull Entry<UUID, Integer> entry) -> new VehicleAssigneeEntry(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static List<VehicleIdentityEntry> vehicleIdentityEntries(FarAndWideSavedData data) {
        return data.getVehicleIdentitiesByUuid().entrySet().stream()
                .map((Map.@NonNull Entry<UUID, FarAndWideSavedData.VehicleIdentity> entry)
                        -> new VehicleIdentityEntry(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static List<VehicleLocationEntry> vehicleLocationEntries(FarAndWideSavedData data) {
        return data.getVehicleLocationsByUuid().entrySet().stream()
                .map((Map.@NonNull Entry<UUID, FarAndWideSavedData.VehicleLocation> entry)
                        -> new VehicleLocationEntry(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static List<DeathRouteEntry> deathRouteEntries(FarAndWideSavedData data) {
        return data.getDeathRoutesByPlayerUuid().entrySet().stream()
                .map((Map.@NonNull Entry<UUID, Integer> entry)
                        -> new DeathRouteEntry(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static FarAndWideSavedData savedData(int dataVersion, int nextRouteId, int nextAssigneeId,
            int nextWaypointId,
            List<Route> routes, List<AssignmentEntry> assignments, List<SelectedRouteEntry> selectedRoutes,
            List<VehicleAssigneeEntry> vehicleAssignees, List<VehicleIdentityEntry> vehicleIdentities,
            List<VehicleLocationEntry> vehicleLocations, List<DeathRouteEntry> deathRoutes, List<CargoOrder> orders) {
        Map<Integer, RouteAssignment> assignmentsByAssignee = assignments.stream().collect(Collectors.toMap(
                entry -> entry.assigneeId(), entry -> entry.assignment(), (first, ignored) -> first));
        Map<Integer, Integer> selectedRouteByAssignee = selectedRoutes.stream().collect(Collectors.toMap(
                entry -> entry.assigneeId(), entry -> entry.routeId(), (first, ignored) -> first));
        Map<UUID, Integer> vehicleAssigneeByUuid = vehicleAssignees.stream().collect(Collectors.toMap(
                entry -> entry.vehicleUuid(), entry -> entry.assigneeId(), (first, ignored) -> first));
        Map<UUID, FarAndWideSavedData.VehicleIdentity> vehicleIdentityByUuid = vehicleIdentities.stream()
                .collect(Collectors.toMap(
                        entry -> entry.vehicleUuid(), entry -> entry.identity(), (first, ignored) -> first));
        Map<UUID, FarAndWideSavedData.VehicleLocation> vehicleLocationByUuid = vehicleLocations.stream()
                .collect(Collectors.toMap(
                        entry -> entry.vehicleUuid(), entry -> entry.location(), (first, ignored) -> first));
        Map<UUID, Integer> deathRouteByPlayerUuid = deathRoutes.stream().collect(Collectors.toMap(
                entry -> entry.playerUuid(), entry -> entry.routeId(), (first, ignored) -> first));
        FarAndWideSavedData data = FarAndWideSavedData.restore(dataVersion, nextRouteId, nextAssigneeId, nextWaypointId, routes,
                assignmentsByAssignee, selectedRouteByAssignee, vehicleAssigneeByUuid, vehicleIdentityByUuid,
                vehicleLocationByUuid, deathRouteByPlayerUuid);
        data.restoreOrders(orders);
        return data;
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

    private record VehicleAssigneeEntry(UUID vehicleUuid, int assigneeId) {
    }

    private record VehicleIdentityEntry(UUID vehicleUuid, FarAndWideSavedData.VehicleIdentity identity) {
    }

    private record VehicleLocationEntry(UUID vehicleUuid, FarAndWideSavedData.VehicleLocation location) {
    }

    private record DeathRouteEntry(UUID playerUuid, int routeId) {
    }
}
