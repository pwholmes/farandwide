package com.lastcallsoftware.farandwide.vehicle;

import com.lastcallsoftware.farandwide.vehicle.navigation.NavigationIntent;

import net.minecraft.world.entity.Entity;

/** Applies a navigation intent using a specific vehicle's movement mechanics. */
public interface VehicleActuator {
    boolean supports(Entity vehicle);

    boolean isAuthoritative(Entity vehicle);

    void apply(Entity vehicle, NavigationIntent intent);

    void stop(Entity vehicle);
}
