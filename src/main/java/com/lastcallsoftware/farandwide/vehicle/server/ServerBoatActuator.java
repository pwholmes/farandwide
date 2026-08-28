package com.lastcallsoftware.farandwide.vehicle.server;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.vehicle.BoatControls;
import com.lastcallsoftware.farandwide.vehicle.BoatVehicleSupport;
import com.lastcallsoftware.farandwide.vehicle.VehicleActuator;
import com.lastcallsoftware.farandwide.vehicle.navigation.NavigationIntent;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;

/** Applies boat physics on the server when no controlling client owns them. */
final class ServerBoatActuator implements VehicleActuator {
    @Override
    public boolean supports(Entity vehicle) {
        return BoatVehicleSupport.supportsNavigation(vehicle);
    }

    @Override
    public boolean isAuthoritative(Entity vehicle) {
        return !vehicle.isClientAuthoritative();
    }

    @Override
    public void apply(Entity vehicle, NavigationIntent intent) {
        AbstractBoat boat = (AbstractBoat) vehicle;
        BoatControls.Input input = BoatControls.from(
                boat.getYRot(), intent, Constants.Vehicles.BOATS_MOVE_WHILE_TURNING);
        boat.setYRot(boat.getYRot() + Mth.clamp(input.yawError(), -Constants.Vehicles.SERVER_BOAT_MAX_TURN_PER_TICK,
                Constants.Vehicles.SERVER_BOAT_MAX_TURN_PER_TICK));
        if (!input.forward()) {
            boat.setDeltaMovement(Vec3.ZERO);
            return;
        }
        float yawRadians = boat.getYRot() * ((float) Math.PI / 180.0F);
        Vec3 acceleration = new Vec3(-Mth.sin(yawRadians) * Constants.Vehicles.SERVER_BOAT_ACCELERATION, 0.0,
                Mth.cos(yawRadians) * Constants.Vehicles.SERVER_BOAT_ACCELERATION);
        boat.setDeltaMovement(boat.getDeltaMovement().add(acceleration));
    }

    @Override
    public void stop(Entity vehicle) {
        vehicle.setDeltaMovement(Vec3.ZERO);
    }
}
