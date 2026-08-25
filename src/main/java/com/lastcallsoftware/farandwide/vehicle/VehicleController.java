package com.lastcallsoftware.farandwide.vehicle;

import net.minecraft.world.entity.vehicle.boat.Boat;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.route.RouteManager;
import com.lastcallsoftware.farandwide.route.Waypoint;

import net.minecraft.client.Minecraft;

public class VehicleController {
    private static final double ARRIVAL_RADIUS = 5.0;

    public static void register() {
        NeoForge.EVENT_BUS.addListener(VehicleController::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Boat boat = getControlledBoat();
        if (boat == null) {
            return;
        }

        RouteAssignment assignment = RouteManager.getActiveAssignment(boat);
        if (assignment == null) {
            return;
        }

        Route route = RouteManager.getRoute(assignment.getRouteId());
        Waypoint target = RouteManager.getTargetWaypoint(assignment);
        if (route == null || target == null) {
            assignment.setActive(false);
            return;
        }

        double deltaX = boat.getX() - target.position().x;
        double deltaZ = boat.getZ() - target.position().z;
        if (deltaX * deltaX + deltaZ * deltaZ <= ARRIVAL_RADIUS * ARRIVAL_RADIUS) {
            int nextIndex = assignment.getTargetWaypointIndex() + 1;
            if (nextIndex >= route.getWaypoints().size()) {
                assignment.setActive(false);
                return;
            }
            assignment.setTargetWaypointIndex(nextIndex);
        }

        boat.setInput(false, false, true, false);
    }

    private static Boat getControlledBoat() {
        if (Minecraft.getInstance().player == null) {
            return null;
        }

        if (Minecraft.getInstance().player.getVehicle() instanceof Boat boat) {
            return boat;
        }

        return null;
    }
}
