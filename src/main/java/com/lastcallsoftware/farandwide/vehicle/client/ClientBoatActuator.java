package com.lastcallsoftware.farandwide.vehicle.client;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.vehicle.BoatControls;
import com.lastcallsoftware.farandwide.vehicle.BoatVehicleSupport;
import com.lastcallsoftware.farandwide.vehicle.VehicleActuator;
import com.lastcallsoftware.farandwide.vehicle.navigation.NavigationIntent;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;

/** Uses vanilla input on the client that owns a player-ridden boat's physics. */
final class ClientBoatActuator implements VehicleActuator {
    @Override
    public boolean supports(Entity vehicle) {
        return BoatVehicleSupport.supportsNavigation(vehicle);
    }

    @Override
    public boolean isAuthoritative(Entity vehicle) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.getVehicle() == vehicle;
    }

    @Override
    public void apply(Entity vehicle, NavigationIntent intent) {
        AbstractBoat boat = (AbstractBoat) vehicle;
        BoatControls.Input input = BoatControls.from(
                boat.getYRot(), intent, Constants.Vehicles.BOATS_MOVE_WHILE_TURNING);
        boat.setInput(input.left(), input.right(), input.forward(), input.backward());
    }

    @Override
    public void stop(Entity vehicle) {
        ((AbstractBoat) vehicle).setInput(false, false, false, false);
    }
}
