package com.lastcallsoftware.farandwide.vehicle.navigation;

import com.lastcallsoftware.farandwide.route.Waypoint;

import net.minecraft.world.entity.Entity;

/** Treats consecutive waypoints as unobstructed straight-line segments. */
public final class DirectWaypointNavigator implements VehicleNavigator {
    @Override
    public boolean supports(Entity vehicle) {
        return true;
    }

    @Override
    public NavigationIntent navigate(Entity vehicle, Waypoint target) {
        return NavigationIntent.toward(vehicle.position(), target.position());
    }
}
