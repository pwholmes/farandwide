package com.lastcallsoftware.farandwide.vehicle.client;

import com.lastcallsoftware.farandwide.vehicle.HorseControls;
import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.vehicle.EquineVehicleSupport;
import com.lastcallsoftware.farandwide.vehicle.VehicleActuator;
import com.lastcallsoftware.farandwide.vehicle.navigation.NavigationIntent;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Supplies shared equine input to client-ridden horses, donkeys, and mules. */
final class ClientHorseActuator implements VehicleActuator {
    @Override
    public boolean supports(Entity vehicle) {
        return EquineVehicleSupport.supportsNavigation(vehicle);
    }

    @Override
    public boolean isAuthoritative(Entity vehicle) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.getVehicle() == vehicle;
    }

    @Override
    public void apply(Entity vehicle, NavigationIntent intent) {
        Player rider = Minecraft.getInstance().player;
        if (rider == null) {
            return;
        }

        HorseControls.Input input = HorseControls.from(
                vehicle.getYRot(), intent, Constants.Vehicles.EQUINES_MOVE_WHILE_TURNING);
        rider.setYRot(input.yaw());
        rider.xxa = 0.0F;
        rider.zza = input.forward();
    }

    @Override
    public void stop(Entity vehicle) {
        Player rider = Minecraft.getInstance().player;
        if (rider != null) {
            rider.xxa = 0.0F;
            rider.zza = 0.0F;
        }
    }
}
