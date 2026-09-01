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
    private int turnInputTick;

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
        boolean turning = input.left() || input.right();
        boolean applyTurn = !turning || shouldApplyTurnInput(turnInputTick++);
        if (!turning) {
            turnInputTick = 0;
        }
        boat.setInput(
                applyTurn && input.left(),
                applyTurn && input.right(),
                input.forward(),
                input.backward());
    }

    @Override
    public void stop(Entity vehicle) {
        turnInputTick = 0;
        ((AbstractBoat) vehicle).setInput(false, false, false, false);
    }

    static boolean shouldApplyTurnInput(int turnInputTick) {
        return turnInputTick % Constants.Vehicles.CLIENT_BOAT_TURN_INPUT_INTERVAL_TICKS == 0;
    }
}
