package com.lastcallsoftware.farandwide.route.network.payload;

import java.util.List;

import com.lastcallsoftware.farandwide.route.persistence.FarAndWideSavedData;
import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.TraversalType;
import com.lastcallsoftware.farandwide.route.Waypoint;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
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
    private static final int MAX_ROUTES = 1_024;
    private static final int MAX_WAYPOINTS_PER_ROUTE = 16_384;
    private static final int MAX_ROUTE_NAME_LENGTH = 64;
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
        if (payload.routes.size() > MAX_ROUTES) {
            throw new IllegalArgumentException("Too many routes to synchronize");
        }
        buffer.writeVarInt(payload.selectedRouteId);
        buffer.writeCollection(payload.routes, (target, route) -> route.write(target));
    }

    private static RouteSnapshotPayload read(RegistryFriendlyByteBuf buffer) {
        int selectedRouteId = buffer.readVarInt();
        List<RouteSnapshot> routes = buffer.readList(RouteSnapshot::read);
        if (routes.size() > MAX_ROUTES) {
            throw new IllegalArgumentException("Received too many routes");
        }
        return new RouteSnapshotPayload(routes, selectedRouteId);
    }

    public record RouteSnapshot(int id, String name, TraversalType traversalType, List<Waypoint> waypoints) {
        private static RouteSnapshot from(Route route) {
            return new RouteSnapshot(route.getId(), route.getName(), route.getTraversalType(), route.getWaypoints());
        }

        public Route toRoute() {
            return new Route(id, name, traversalType, waypoints);
        }

        private void write(FriendlyByteBuf buffer) {
            if (name.length() > MAX_ROUTE_NAME_LENGTH || waypoints.size() > MAX_WAYPOINTS_PER_ROUTE) {
                throw new IllegalArgumentException("Route exceeds synchronization limits");
            }
            buffer.writeVarInt(id);
            buffer.writeUtf(name, MAX_ROUTE_NAME_LENGTH);
            buffer.writeVarInt(traversalType.ordinal());
            buffer.writeCollection(waypoints, (target, waypoint) -> {
                target.writeDouble(waypoint.position().x);
                target.writeDouble(waypoint.position().y);
                target.writeDouble(waypoint.position().z);
                target.writeUtf(waypoint.dimension().toString());
            });
        }

        private static RouteSnapshot read(FriendlyByteBuf buffer) {
            int id = buffer.readVarInt();
            String name = buffer.readUtf(MAX_ROUTE_NAME_LENGTH);
            int traversalOrdinal = buffer.readVarInt();
            if (traversalOrdinal < 0 || traversalOrdinal >= TraversalType.values().length) {
                throw new IllegalArgumentException("Unknown route traversal type");
            }
            List<Waypoint> waypoints = buffer.readList(source -> new Waypoint(
                    new Vec3(source.readDouble(), source.readDouble(), source.readDouble()),
                    Identifier.parse(source.readUtf())));
            if (waypoints.size() > MAX_WAYPOINTS_PER_ROUTE) {
                throw new IllegalArgumentException("Received too many waypoints");
            }
            return new RouteSnapshot(id, name, TraversalType.values()[traversalOrdinal], waypoints);
        }
    }
}
