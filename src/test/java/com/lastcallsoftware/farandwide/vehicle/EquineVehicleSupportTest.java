package com.lastcallsoftware.farandwide.vehicle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Mule;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import org.junit.jupiter.api.Test;

class EquineVehicleSupportTest {
    @Test
    void navigationExplicitlyCoversHorseDonkeyAndMule() {
        assertTrue(EquineVehicleSupport.supportsNavigationType(Horse.class));
        assertTrue(EquineVehicleSupport.supportsNavigationType(Donkey.class));
        assertTrue(EquineVehicleSupport.supportsNavigationType(Mule.class));
        assertFalse(EquineVehicleSupport.supportsNavigationType(SkeletonHorse.class));
    }

    @Test
    void cargoTypeCoverageExcludesOrdinaryHorses() {
        assertFalse(EquineVehicleSupport.supportsCargoType(Horse.class));
        assertTrue(EquineVehicleSupport.supportsCargoType(Donkey.class));
        assertTrue(EquineVehicleSupport.supportsCargoType(Mule.class));
        assertFalse(EquineVehicleSupport.hasCargoStorage(Donkey.class, false));
        assertTrue(EquineVehicleSupport.hasCargoStorage(Donkey.class, true));
        assertTrue(EquineVehicleSupport.hasCargoStorage(Mule.class, true));
    }
}
