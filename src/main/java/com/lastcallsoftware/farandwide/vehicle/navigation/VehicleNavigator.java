package com.lastcallsoftware.farandwide.vehicle.navigation;

import com.lastcallsoftware.farandwide.route.Waypoint;

import net.minecraft.world.entity.Entity;

/** Plans the local movement needed to traverse the current route segment. */
public interface VehicleNavigator {
    boolean supports(Entity vehicle);

    NavigationIntent navigate(Entity vehicle, Waypoint target);
}
