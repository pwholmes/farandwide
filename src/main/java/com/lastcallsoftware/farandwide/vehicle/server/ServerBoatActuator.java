package com.lastcallsoftware.farandwide.vehicle.server;

import com.lastcallsoftware.farandwide.vehicle.BoatControls;
import com.lastcallsoftware.farandwide.vehicle.VehicleActuator;
import com.lastcallsoftware.farandwide.vehicle.navigation.NavigationIntent;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.phys.Vec3;

/** Applies boat physics on the server when no controlling client owns them. */
final class ServerBoatActuator implements VehicleActuator {
    private static final float MAX_TURN_PER_TICK = 1.0F;
    private static final double ACCELERATION = 0.04;

    @Override
    public boolean supports(Entity vehicle) {
        return vehicle instanceof Boat;
    }

    @Override
    public boolean isAuthoritative(Entity vehicle) {
        return !vehicle.isClientAuthoritative();
    }

    @Override
    public void apply(Entity vehicle, NavigationIntent intent) {
        Boat boat = (Boat) vehicle;
        BoatControls.Input input = BoatControls.from(
                boat.getYRot(), intent, BoatControls.MOVES_WHILE_TURNING);
        boat.setYRot(boat.getYRot() + Mth.clamp(input.yawError(), -MAX_TURN_PER_TICK, MAX_TURN_PER_TICK));
        if (!input.forward()) {
            boat.setDeltaMovement(Vec3.ZERO);
            return;
        }
        float yawRadians = boat.getYRot() * ((float) Math.PI / 180.0F);
        Vec3 acceleration = new Vec3(-Mth.sin(yawRadians) * ACCELERATION, 0.0,
                Mth.cos(yawRadians) * ACCELERATION);
        boat.setDeltaMovement(boat.getDeltaMovement().add(acceleration));
    }

    @Override
    public void stop(Entity vehicle) {
        vehicle.setDeltaMovement(Vec3.ZERO);
    }
}
