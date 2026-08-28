package com.lastcallsoftware.farandwide.vehicle;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.boat.AbstractChestBoat;

/** Explicit movement and cargo coverage for the 26.2 boat hierarchy. */
public final class BoatVehicleSupport {
    private BoatVehicleSupport() {
    }

    public static boolean supportsNavigation(Entity entity) {
        return entity instanceof AbstractBoat;
    }

    public static boolean supportsNavigationType(Class<? extends Entity> entityType) {
        return AbstractBoat.class.isAssignableFrom(entityType);
    }

    public static boolean supportsCargoType(Class<? extends Entity> entityType) {
        return AbstractChestBoat.class.isAssignableFrom(entityType);
    }
}
