package com.lastcallsoftware.farandwide.vehicle;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.vehicle.navigation.NavigationIntent;

import net.minecraft.util.Mth;

/** Shared equine steering for horses, donkeys, and mules. */
public final class HorseControls {
    private HorseControls() {
    }

    public static Input from(float currentYaw, NavigationIntent intent, boolean movesWhileTurning) {
        float desiredYaw = (float) Math.toDegrees(Math.atan2(-intent.direction().x, intent.direction().z));
        float yawError = Mth.wrapDegrees(desiredYaw - currentYaw);
        return new Input(
                currentYaw + Mth.clamp(yawError, -Constants.Vehicles.EQUINE_MAX_TURN_PER_TICK,
                        Constants.Vehicles.EQUINE_MAX_TURN_PER_TICK),
                movesWhileTurning || Math.abs(yawError) <= Constants.Vehicles.EQUINE_FACING_TARGET_TOLERANCE ? 1.0F : 0.0F,
                Math.abs(yawError) <= Constants.Vehicles.EQUINE_FACING_TARGET_TOLERANCE);
    }

    public record Input(float yaw, float forward, boolean facingTarget) {
    }
}
