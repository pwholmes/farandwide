package com.lastcallsoftware.farandwide.vehicle.client;

import com.lastcallsoftware.farandwide.vehicle.HorseControls;
import com.lastcallsoftware.farandwide.vehicle.VehicleActuator;
import com.lastcallsoftware.farandwide.vehicle.navigation.NavigationIntent;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;

/** Supplies rider input to the horse owned by the local client. */
final class ClientHorseActuator implements VehicleActuator {
    @Override
    public boolean supports(Entity vehicle) {
        return vehicle instanceof AbstractHorse;
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
                vehicle.getYRot(), intent, HorseControls.MOVES_WHILE_TURNING);
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
