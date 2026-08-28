package com.lastcallsoftware.farandwide.route.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lastcallsoftware.farandwide.route.persistence.FarAndWideSavedData;
import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.CargoBehavior;
import com.lastcallsoftware.farandwide.route.CargoFilter;
import com.lastcallsoftware.farandwide.route.CargoOperation;
import com.lastcallsoftware.farandwide.route.CargoStationBinding;
import com.lastcallsoftware.farandwide.route.TraversalType;
import com.lastcallsoftware.farandwide.route.Waypoint;
import com.lastcallsoftware.farandwide.route.WaypointAction;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class RouteSnapshotPayloadTest {
    @Test
    void snapshotReconstructsEquivalentRoutes() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route original = data.createRoute();
        data.renameRoute(original.getId(), "Nether Link");
        data.setTraversalType(original.getId(), TraversalType.LOOP);
        data.addWaypoint(original.getId(), new Waypoint(
                new Vec3(12.5, 70, -3), Identifier.parse("minecraft:overworld")));
        data.addWaypoint(original.getId(), new Waypoint(
                new Vec3(-2, 80.25, 9), Identifier.parse("minecraft:the_nether")));
        original = data.getRoute(original.getId());

        RouteSnapshotPayload payload = RouteSnapshotPayload.from(data, original.getId());
        List<Route> reconstructed = payload.routes().stream()
                .map(RouteSnapshotPayload.RouteSnapshot::toRoute)
                .toList();

        assertEquals(original.getId(), payload.selectedRouteId());
        assertEquals(1, reconstructed.size());
        assertRouteEquals(original, reconstructed.getFirst());
    }

    @Test
    void wireRoundTripPreservesWaypointIdentityAndCargoSettings() {
        Identifier coal = Identifier.parse("minecraft:coal");
        Identifier iron = Identifier.parse("minecraft:iron_ingot");
        CargoBehavior behavior = new CargoBehavior(
                CargoOperation.UNLOAD_THEN_LOAD,
                CargoFilter.allowList(List.of(coal)),
                CargoFilter.allowList(List.of(iron)),
                Optional.of(new CargoStationBinding(new BlockPos(3, 65, -4), Direction.EAST)),
                Optional.of(new CargoStationBinding(new BlockPos(2, 65, -4), Direction.WEST)));
        Waypoint waypoint = new Waypoint(
                73, new Vec3(2, 65, -4), Identifier.parse("minecraft:overworld"),
                WaypointAction.cargo(behavior));
        Route route = new Route(8, "Freight", TraversalType.REVERSE, List.of(waypoint));
        RouteSnapshotPayload sent = RouteSnapshotPayload.from(List.of(route), 8);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);

        RouteSnapshotPayload.STREAM_CODEC.encode(buffer, sent);
        RouteSnapshotPayload received = RouteSnapshotPayload.STREAM_CODEC.decode(buffer);

        assertEquals(sent, received);
        assertEquals(route, received.routes().getFirst().toRoute());
    }

    private static void assertRouteEquals(Route expected, Route actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getTraversalType(), actual.getTraversalType());
        assertEquals(expected.getWaypoints(), actual.getWaypoints());
    }
}
