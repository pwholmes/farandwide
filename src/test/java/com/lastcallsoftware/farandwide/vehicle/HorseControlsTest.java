package com.lastcallsoftware.farandwide.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.lastcallsoftware.farandwide.vehicle.navigation.NavigationIntent;

import net.minecraft.world.phys.Vec3;

class HorseControlsTest {
    @Test
    void movesForwardWithoutTurningWhenTargetIsAhead() {
        HorseControls.Input input = HorseControls.from(0.0F,
                NavigationIntent.toward(Vec3.ZERO, new Vec3(0.0, 0.0, 1.0)), false);

        assertEquals(0.0F, input.yaw());
        assertEquals(1.0F, input.forward());
        assertTrue(input.facingTarget());
    }

    @Test
    void limitsTurningToFourDegreesPerTick() {
        HorseControls.Input left = HorseControls.from(0.0F,
                NavigationIntent.toward(Vec3.ZERO, new Vec3(1.0, 0.0, 0.0)), false);
        HorseControls.Input right = HorseControls.from(0.0F,
                NavigationIntent.toward(Vec3.ZERO, new Vec3(-1.0, 0.0, 0.0)), false);

        assertEquals(-4.0F, left.yaw());
        assertEquals(4.0F, right.yaw());
        assertFalse(left.facingTarget());
        assertFalse(right.facingTarget());
        assertEquals(0.0F, left.forward());
        assertEquals(0.0F, right.forward());
    }

    @Test
    void canMoveWhileTurningWhenEnabled() {
        HorseControls.Input input = HorseControls.from(0.0F,
                NavigationIntent.toward(Vec3.ZERO, new Vec3(1.0, 0.0, 0.0)), true);

        assertEquals(1.0F, input.forward());
        assertFalse(input.facingTarget());
    }

    @Test
    void ignoresTinyHeadingCorrectionsToPreventSteeringWiggle() {
        double desiredYaw = Math.toRadians(0.5);
        Vec3 target = new Vec3(-Math.sin(desiredYaw), 0.0, Math.cos(desiredYaw));

        HorseControls.Input input = HorseControls.from(
                0.0F, NavigationIntent.toward(Vec3.ZERO, target), false);

        assertEquals(0.0F, input.yaw());
        assertEquals(1.0F, input.forward());
        assertTrue(input.facingTarget());
    }
}
