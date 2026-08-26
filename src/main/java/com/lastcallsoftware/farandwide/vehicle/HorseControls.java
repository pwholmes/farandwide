package com.lastcallsoftware.farandwide.vehicle;

import com.lastcallsoftware.farandwide.vehicle.navigation.NavigationIntent;

import net.minecraft.util.Mth;

/** Converts side-neutral movement intent into the heading used by ridden horses. */
public final class HorseControls {
    public static final boolean MOVES_WHILE_TURNING = false;
    private static final float MAX_TURN_PER_TICK = 4.0F;
    private static final float FACING_TARGET_TOLERANCE = 5.0F;

    private HorseControls() {
    }

    public static Input from(float currentYaw, NavigationIntent intent, boolean movesWhileTurning) {
        float desiredYaw = (float) Math.toDegrees(Math.atan2(-intent.direction().x, intent.direction().z));
        float yawError = Mth.wrapDegrees(desiredYaw - currentYaw);
        return new Input(
                currentYaw + Mth.clamp(yawError, -MAX_TURN_PER_TICK, MAX_TURN_PER_TICK),
                movesWhileTurning || Math.abs(yawError) <= FACING_TARGET_TOLERANCE ? 1.0F : 0.0F,
                Math.abs(yawError) <= FACING_TARGET_TOLERANCE);
    }

    public record Input(float yaw, float forward, boolean facingTarget) {
    }
}
