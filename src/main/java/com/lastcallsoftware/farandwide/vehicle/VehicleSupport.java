package com.lastcallsoftware.farandwide.vehicle;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;

/** Identifies entities that can follow a route. */
public final class VehicleSupport {
    private VehicleSupport() {
    }

    public static boolean supportsNavigation(Entity entity) {
        return BoatVehicleSupport.supportsNavigation(entity)
                || EquineVehicleSupport.supportsNavigation(entity)
                || entity instanceof AbstractMinecart;
    }
}
