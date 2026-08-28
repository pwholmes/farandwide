package com.lastcallsoftware.farandwide.route.network.payload;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.lastcallsoftware.farandwide.route.persistence.FarAndWideSavedData;
import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.route.CargoBehavior;
import com.lastcallsoftware.farandwide.route.CargoFilter;
import com.lastcallsoftware.farandwide.route.CargoOperation;
import com.lastcallsoftware.farandwide.route.CargoStationBinding;
import com.lastcallsoftware.farandwide.route.TraversalType;
import com.lastcallsoftware.farandwide.route.Waypoint;
import com.lastcallsoftware.farandwide.route.WaypointAction;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Server-to-client replacement snapshot of all route definitions.
 *
 * <p>Snapshots contain immutable copies rather than domain references shared with
 * server storage. {@code selectedRouteId} is player-specific in direct replies;
 * broadcasts use zero so clients preserve their existing selection while replacing
 * route definitions. Assignments use a separate payload because they are scoped to
 * the player or vehicle currently controlled by the receiving client.
 *
 * <p>Collection and string limits protect decoding from unbounded allocations.
 * Keep read and write ordering exactly symmetric when adding fields.
 */
public record RouteSnapshotPayload(List<RouteSnapshot> routes, int selectedRouteId) implements CustomPacketPayload {
    public static final Type<RouteSnapshotPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("farandwide", "route_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RouteSnapshotPayload> STREAM_CODEC = StreamCodec.of(
            RouteSnapshotPayload::write,
            RouteSnapshotPayload::read);

    public static RouteSnapshotPayload from(FarAndWideSavedData data) {
        return from(data, 0);
    }

    public static RouteSnapshotPayload from(FarAndWideSavedData data, int selectedRouteId) {
        return from(data.getRoutes(), selectedRouteId);
    }

    public static RouteSnapshotPayload from(List<Route> routes, int selectedRouteId) {
        return new RouteSnapshotPayload(routes.stream().map(RouteSnapshot::from).toList(), selectedRouteId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, RouteSnapshotPayload payload) {
        if (payload.routes.size() > Constants.Network.MAX_ROUTES) {
            throw new IllegalArgumentException("Too many routes to synchronize");
        }
        buffer.writeVarInt(payload.selectedRouteId);
        buffer.writeCollection(payload.routes, (target, route) -> route.write(target));
    }

    private static RouteSnapshotPayload read(RegistryFriendlyByteBuf buffer) {
        int selectedRouteId = buffer.readVarInt();
        List<RouteSnapshot> routes = readBoundedList(buffer, Constants.Network.MAX_ROUTES, RouteSnapshot::read, "routes");
        return new RouteSnapshotPayload(routes, selectedRouteId);
    }

    private static <T> List<T> readBoundedList(FriendlyByteBuf buffer, int maximum,
            Function<FriendlyByteBuf, T> decoder, String description) {
        int size = buffer.readVarInt();
        if (size < 0 || size > maximum) {
            throw new IllegalArgumentException("Received too many " + description);
        }
        List<T> values = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            values.add(decoder.apply(buffer));
        }
        return values;
    }

    public record RouteSnapshot(int id, String name, TraversalType traversalType, List<Waypoint> waypoints) {
        private static RouteSnapshot from(Route route) {
            return new RouteSnapshot(route.getId(), route.getName(), route.getTraversalType(), route.getWaypoints());
        }

        public Route toRoute() {
            return new Route(id, name, traversalType, waypoints);
        }

        private void write(FriendlyByteBuf buffer) {
            if (name.length() > Constants.Network.MAX_ROUTE_NAME_LENGTH
                    || waypoints.size() > Constants.Network.MAX_WAYPOINTS_PER_ROUTE) {
                throw new IllegalArgumentException("Route exceeds synchronization limits");
            }
            buffer.writeVarInt(id);
            buffer.writeUtf(name, Constants.Network.MAX_ROUTE_NAME_LENGTH);
            buffer.writeVarInt(traversalType.ordinal());
            buffer.writeCollection(waypoints, (target, waypoint) -> {
                target.writeVarInt(waypoint.id());
                target.writeDouble(waypoint.position().x);
                target.writeDouble(waypoint.position().y);
                target.writeDouble(waypoint.position().z);
                target.writeUtf(waypoint.dimension().toString(), Constants.Network.MAX_IDENTIFIER_LENGTH);
                target.writeDouble(waypoint.arrivalRadius());
                if (waypoint.action() instanceof WaypointAction.Cargo cargo) {
                    target.writeBoolean(true);
                    writeCargoBehavior(target, cargo.behavior());
                } else {
                    target.writeBoolean(false);
                }
            });
        }

        private static RouteSnapshot read(FriendlyByteBuf buffer) {
            int id = buffer.readVarInt();
            String name = buffer.readUtf(Constants.Network.MAX_ROUTE_NAME_LENGTH);
            int traversalOrdinal = buffer.readVarInt();
            if (traversalOrdinal < 0 || traversalOrdinal >= TraversalType.values().length) {
                throw new IllegalArgumentException("Unknown route traversal type");
            }
            List<Waypoint> waypoints = readBoundedList(buffer, Constants.Network.MAX_WAYPOINTS_PER_ROUTE,
                    RouteSnapshot::readWaypoint, "waypoints");
            return new RouteSnapshot(id, name, TraversalType.values()[traversalOrdinal], waypoints);
        }

        private static Waypoint readWaypoint(FriendlyByteBuf source) {
            int waypointId = source.readVarInt();
            Vec3 position = new Vec3(source.readDouble(), source.readDouble(), source.readDouble());
            Identifier dimension = Identifier.parse(source.readUtf(Constants.Network.MAX_IDENTIFIER_LENGTH));
            double arrivalRadius = source.readDouble();
            WaypointAction action = source.readBoolean()
                    ? WaypointAction.cargo(readCargoBehavior(source))
                    : WaypointAction.normal();
            return new Waypoint(waypointId, position, dimension, action, arrivalRadius);
        }

        private static void writeCargoBehavior(FriendlyByteBuf buffer, CargoBehavior behavior) {
            buffer.writeVarInt(behavior.operation().ordinal());
            writeFilter(buffer, behavior.loadFilter());
            writeFilter(buffer, behavior.unloadFilter());
            writeStation(buffer, behavior.loadStation());
            writeStation(buffer, behavior.unloadStation());
        }

        private static CargoBehavior readCargoBehavior(FriendlyByteBuf buffer) {
            return new CargoBehavior(
                    readEnum(buffer, CargoOperation.values(), "cargo operation"),
                    readFilter(buffer),
                    readFilter(buffer),
                    readStation(buffer),
                    readStation(buffer));
        }

        private static void writeStation(FriendlyByteBuf buffer, java.util.Optional<CargoStationBinding> station) {
            buffer.writeBoolean(station.isPresent());
            station.ifPresent(binding -> {
                buffer.writeInt(binding.position().getX());
                buffer.writeInt(binding.position().getY());
                buffer.writeInt(binding.position().getZ());
                buffer.writeVarInt(binding.accessSide().ordinal());
            });
        }

        private static java.util.Optional<CargoStationBinding> readStation(FriendlyByteBuf buffer) {
            if (!buffer.readBoolean()) {
                return java.util.Optional.empty();
            }
            BlockPos position = new BlockPos(buffer.readInt(), buffer.readInt(), buffer.readInt());
            Direction side = readEnum(buffer, Direction.values(), "cargo station side");
            return java.util.Optional.of(new CargoStationBinding(position, side));
        }

        private static void writeFilter(FriendlyByteBuf buffer, CargoFilter filter) {
            if (filter.itemIds().size() > Constants.Network.MAX_FILTER_ITEMS) {
                throw new IllegalArgumentException("Cargo filter exceeds synchronization limits");
            }
            buffer.writeVarInt(filter.mode().ordinal());
            buffer.writeCollection(filter.itemIds(),
                    (target, itemId) -> target.writeUtf(itemId.toString(), Constants.Network.MAX_IDENTIFIER_LENGTH));
        }

        private static CargoFilter readFilter(FriendlyByteBuf buffer) {
            CargoFilter.Mode mode = readEnum(buffer, CargoFilter.Mode.values(), "cargo filter mode");
            int itemCount = buffer.readVarInt();
            if (itemCount < 0 || itemCount > Constants.Network.MAX_FILTER_ITEMS) {
                throw new IllegalArgumentException("Received too many cargo filter items");
            }
            List<Identifier> itemIds = new ArrayList<>(itemCount);
            for (int index = 0; index < itemCount; index++) {
                itemIds.add(Identifier.parse(buffer.readUtf(Constants.Network.MAX_IDENTIFIER_LENGTH)));
            }
            return new CargoFilter(mode, itemIds);
        }

        private static <E extends Enum<E>> E readEnum(FriendlyByteBuf buffer, E[] values, String description) {
            int ordinal = buffer.readVarInt();
            if (ordinal < 0 || ordinal >= values.length) {
                throw new IllegalArgumentException("Unknown " + description);
            }
            return values[ordinal];
        }
    }
}
