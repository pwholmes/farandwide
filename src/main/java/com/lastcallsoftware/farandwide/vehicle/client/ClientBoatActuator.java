package com.lastcallsoftware.farandwide.vehicle.client;

import com.lastcallsoftware.farandwide.vehicle.BoatControls;
import com.lastcallsoftware.farandwide.vehicle.VehicleActuator;
import com.lastcallsoftware.farandwide.vehicle.navigation.NavigationIntent;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.Boat;

/** Uses vanilla input on the client that owns a player-ridden boat's physics. */
final class ClientBoatActuator implements VehicleActuator {
    @Override
    public boolean supports(Entity vehicle) {
        return vehicle instanceof Boat;
    }

    @Override
    public boolean isAuthoritative(Entity vehicle) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.getVehicle() == vehicle;
    }

    @Override
    public void apply(Entity vehicle, NavigationIntent intent) {
        Boat boat = (Boat) vehicle;
        BoatControls.Input input = BoatControls.from(
                boat.getYRot(), intent, BoatControls.MOVES_WHILE_TURNING);
        boat.setInput(input.left(), input.right(), input.forward(), input.backward());
    }

    @Override
    public void stop(Entity vehicle) {
        ((Boat) vehicle).setInput(false, false, false, false);
    }
}
