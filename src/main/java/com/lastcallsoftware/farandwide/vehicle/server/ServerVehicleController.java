package com.lastcallsoftware.farandwide.vehicle.server;

import java.util.List;

import com.lastcallsoftware.farandwide.route.Waypoint;
import com.lastcallsoftware.farandwide.vehicle.VehicleActuator;
import com.lastcallsoftware.farandwide.vehicle.navigation.DirectWaypointNavigator;
import com.lastcallsoftware.farandwide.vehicle.navigation.NavigationIntent;
import com.lastcallsoftware.farandwide.vehicle.navigation.VehicleNavigator;

import net.minecraft.world.entity.Entity;

/** Selects a vehicle-specific navigator while keeping movement server-authoritative. */
public final class ServerVehicleController {
    private static final List<VehicleNavigator> NAVIGATORS = List.of(new DirectWaypointNavigator());
    private static final List<VehicleActuator> ACTUATORS = List.of(new ServerBoatActuator(), new ServerHorseActuator());

    private ServerVehicleController() {
    }

    public static void navigate(Entity vehicle, Waypoint target) {
        VehicleNavigator navigator = findNavigator(vehicle);
        VehicleActuator actuator = findActuator(vehicle);
        if (navigator != null && actuator != null && actuator.isAuthoritative(vehicle)) {
            NavigationIntent intent = navigator.navigate(vehicle, target);
            actuator.apply(vehicle, intent);
        }
    }

    public static void stop(Entity vehicle) {
        VehicleActuator actuator = findActuator(vehicle);
        if (actuator != null && actuator.isAuthoritative(vehicle)) {
            actuator.stop(vehicle);
        }
    }

    private static VehicleNavigator findNavigator(Entity vehicle) {
        return NAVIGATORS.stream().filter(navigator -> navigator.supports(vehicle)).findFirst().orElse(null);
    }

    private static VehicleActuator findActuator(Entity vehicle) {
        return ACTUATORS.stream().filter(actuator -> actuator.supports(vehicle)).findFirst().orElse(null);
    }
}
