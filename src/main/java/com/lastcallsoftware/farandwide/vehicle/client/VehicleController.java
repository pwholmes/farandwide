package com.lastcallsoftware.farandwide.vehicle.client;

import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.route.Waypoint;
import com.lastcallsoftware.farandwide.route.client.RouteManager;
import com.lastcallsoftware.farandwide.vehicle.VehicleActuator;
import com.lastcallsoftware.farandwide.vehicle.navigation.DirectWaypointNavigator;
import com.lastcallsoftware.farandwide.vehicle.navigation.NavigationIntent;
import com.lastcallsoftware.farandwide.vehicle.navigation.VehicleNavigator;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Applies navigation input for vehicles whose vanilla physics is client-authoritative. */
public final class VehicleController {
    private static final List<VehicleNavigator> NAVIGATORS = List.of(new DirectWaypointNavigator());
    private static final List<VehicleActuator> ACTUATORS = List.of(new ClientBoatActuator());

    private VehicleController() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(VehicleController::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        Entity ridden = minecraft.player.getVehicle();
        if (ridden == null) {
            return;
        }

        RouteAssignment assignment = RouteManager.getAssignment(ridden.getId());
        Waypoint target = assignment != null && assignment.isActive()
                ? RouteManager.getTargetWaypoint(assignment)
                : null;
        VehicleActuator actuator = ACTUATORS.stream()
                .filter(candidate -> candidate.supports(ridden) && candidate.isAuthoritative(ridden))
                .findFirst().orElse(null);
        if (actuator == null) {
            return;
        }

        if (target == null || !target.dimension().equals(ridden.level().dimension().identifier())) {
            actuator.stop(ridden);
            return;
        }

        VehicleNavigator navigator = NAVIGATORS.stream().filter(candidate -> candidate.supports(ridden))
                .findFirst().orElse(null);
        if (navigator != null) {
            NavigationIntent intent = navigator.navigate(ridden, target);
            actuator.apply(ridden, intent);
        }
    }
}
