package com.lastcallsoftware.farandwide.vehicle;

import com.lastcallsoftware.farandwide.vehicle.navigation.NavigationIntent;

import net.minecraft.util.Mth;

/** Converts side-neutral movement intent into vanilla boat controls. */
public final class BoatControls {
    public static final boolean MOVES_WHILE_TURNING = true;
    private static final float TURN_DEAD_ZONE_DEGREES = 5.0F;

    private BoatControls() {
    }

    public static Input from(float currentYaw, NavigationIntent intent, boolean movesWhileTurning) {
        float desiredYaw = (float) Math.toDegrees(Math.atan2(-intent.direction().x, intent.direction().z));
        float yawError = Mth.wrapDegrees(desiredYaw - currentYaw);
        return new Input(
                yawError < -TURN_DEAD_ZONE_DEGREES,
                yawError > TURN_DEAD_ZONE_DEGREES,
                movesWhileTurning || Math.abs(yawError) <= TURN_DEAD_ZONE_DEGREES,
                false,
                yawError);
    }

    public record Input(boolean left, boolean right, boolean forward, boolean backward, float yawError) {
    }
}
