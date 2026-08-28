package com.lastcallsoftware.farandwide.vehicle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.entity.vehicle.boat.ChestBoat;
import org.junit.jupiter.api.Test;

class BoatVehicleSupportTest {
    @Test
    void navigationCoversOrdinaryAndChestBoats() {
        assertTrue(BoatVehicleSupport.supportsNavigationType(Boat.class));
        assertTrue(BoatVehicleSupport.supportsNavigationType(ChestBoat.class));
    }

    @Test
    void cargoCoverageIsLimitedToChestBoats() {
        assertFalse(BoatVehicleSupport.supportsCargoType(Boat.class));
        assertTrue(BoatVehicleSupport.supportsCargoType(ChestBoat.class));
    }
}
