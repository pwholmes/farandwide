package com.lastcallsoftware.farandwide.route.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lastcallsoftware.farandwide.route.persistence.FarAndWideSavedData;
import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.TraversalType;
import com.lastcallsoftware.farandwide.route.Waypoint;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
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

    private static void assertRouteEquals(Route expected, Route actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getTraversalType(), actual.getTraversalType());
        assertEquals(expected.getWaypoints(), actual.getWaypoints());
    }
}
