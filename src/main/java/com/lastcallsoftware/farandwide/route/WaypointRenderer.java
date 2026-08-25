package com.lastcallsoftware.farandwide.route;

import net.minecraft.gizmos.Gizmos;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

public class WaypointRenderer {

    private WaypointRenderer() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(WaypointRenderer::onClientTick);
        RouteNavigationHud.register();
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Route currentRoute = RouteManager.getCurrentRoute();
        if (currentRoute != null) {
            renderWaypoints(currentRoute);
        }
    }

    public static void renderWaypoints(Route route) {
        RouteAssignment assignment = RouteNavigationHud.getRiddenAssignment();
        int ordinal = 1;
        for (Waypoint waypoint : route.getWaypoints()) {
            boolean target = assignment != null
                    && assignment.getRouteId() == route.getId()
                    && assignment.getTargetWaypointIndex() == ordinal - 1;
            Gizmos.addGizmo(new WaypointGizmo(waypoint.position(), ordinal, target));
            ordinal++;
        }
    }
}
