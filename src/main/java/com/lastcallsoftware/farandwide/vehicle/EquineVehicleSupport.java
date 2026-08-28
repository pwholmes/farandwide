package com.lastcallsoftware.farandwide.vehicle;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Mule;

/** Explicit entity coverage shared by client and server equine actuators. */
public final class EquineVehicleSupport {
    private EquineVehicleSupport() {
    }

    public static boolean supportsNavigation(Entity entity) {
        return entity instanceof Horse || entity instanceof Donkey || entity instanceof Mule;
    }

    public static boolean supportsNavigationType(Class<? extends Entity> entityType) {
        return Horse.class.isAssignableFrom(entityType)
                || Donkey.class.isAssignableFrom(entityType)
                || Mule.class.isAssignableFrom(entityType);
    }

    public static boolean supportsCargoType(Class<? extends Entity> entityType) {
        return Donkey.class.isAssignableFrom(entityType) || Mule.class.isAssignableFrom(entityType);
    }

    public static boolean hasCargoStorage(Class<? extends Entity> entityType, boolean hasChest) {
        return hasChest && supportsCargoType(entityType);
    }
}
