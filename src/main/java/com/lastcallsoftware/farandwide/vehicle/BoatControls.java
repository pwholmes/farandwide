package com.lastcallsoftware.farandwide.vehicle;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.vehicle.navigation.NavigationIntent;

import net.minecraft.util.Mth;

/** Converts side-neutral movement intent into vanilla boat controls. */
public final class BoatControls {
    private BoatControls() {
    }

    public static Input from(float currentYaw, NavigationIntent intent, boolean movesWhileTurning) {
        float desiredYaw = (float) Math.toDegrees(Math.atan2(-intent.direction().x, intent.direction().z));
        float yawError = Mth.wrapDegrees(desiredYaw - currentYaw);
        return new Input(
                yawError < -Constants.Vehicles.BOAT_TURN_DEAD_ZONE_DEGREES,
                yawError > Constants.Vehicles.BOAT_TURN_DEAD_ZONE_DEGREES,
                movesWhileTurning || Math.abs(yawError) <= Constants.Vehicles.BOAT_TURN_DEAD_ZONE_DEGREES,
                false,
                yawError);
    }

    public record Input(boolean left, boolean right, boolean forward, boolean backward, float yawError) {
    }
}
