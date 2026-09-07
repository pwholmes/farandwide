package com.lastcallsoftware.farandwide.route.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.Waypoint;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideSavedData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ServerMountTransitionControllerTest {
    private static final Identifier OVERWORLD = Identifier.parse("minecraft:overworld");

    @Test
    void dismountPreservesExplicitlyClearedSelection() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = routeWithWaypoint(data);
        int playerId = data.allocateAssigneeId();
        int vehicleId = data.allocateAssigneeId();
        data.assignRoute(route.getId(), vehicleId, Vec3.ZERO, OVERWORLD);
        data.setSelectedRouteId(playerId, route.getId());
        data.clearSelectedRouteId(playerId);

        ServerMountTransitionController.applyAssignmentTransition(data, playerId, vehicleId, false);

        assertEquals(0, data.getSelectedRouteId(playerId));
    }

    @Test
    void mountingAssignedVehicleSelectsItsRouteAndRemovesPlayerAssignment() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route playerRoute = routeWithWaypoint(data);
        Route vehicleRoute = routeWithWaypoint(data);
        int playerId = data.allocateAssigneeId();
        int vehicleId = data.allocateAssigneeId();
        data.assignRoute(playerRoute.getId(), playerId, Vec3.ZERO, OVERWORLD);
        data.assignRoute(vehicleRoute.getId(), vehicleId, Vec3.ZERO, OVERWORLD);

        ServerMountTransitionController.applyAssignmentTransition(data, playerId, vehicleId, true);

        assertNull(data.getAssignment(playerId));
        assertEquals(vehicleRoute.getId(), data.getSelectedRouteId(playerId));
    }

    private static Route routeWithWaypoint(FarAndWideSavedData data) {
        Route route = data.createRoute();
        data.addWaypoint(route.getId(), new Waypoint(Vec3.ZERO, OVERWORLD));
        return route;
    }
}
