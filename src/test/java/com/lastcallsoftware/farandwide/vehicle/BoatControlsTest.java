package com.lastcallsoftware.farandwide.vehicle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lastcallsoftware.farandwide.vehicle.navigation.NavigationIntent;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class BoatControlsTest {
    @Test
    void drivesForwardWhenTargetIsAhead() {
        BoatControls.Input input = BoatControls.from(0.0F,
                NavigationIntent.toward(Vec3.ZERO, new Vec3(0, 0, 10)), false);

        assertTrue(input.forward());
        assertFalse(input.left());
        assertFalse(input.right());
    }

    @Test
    void turnsTowardTargetsOnEitherSide() {
        BoatControls.Input left = BoatControls.from(0.0F,
                NavigationIntent.toward(Vec3.ZERO, new Vec3(10, 0, 0)), false);
        BoatControls.Input right = BoatControls.from(0.0F,
                NavigationIntent.toward(Vec3.ZERO, new Vec3(-10, 0, 0)), false);

        assertTrue(left.left());
        assertTrue(right.right());
        assertFalse(left.forward());
        assertFalse(right.forward());
    }

    @Test
    void canMoveWhileTurningWhenEnabled() {
        BoatControls.Input input = BoatControls.from(0.0F,
                NavigationIntent.toward(Vec3.ZERO, new Vec3(10, 0, 0)), true);

        assertTrue(input.left());
        assertTrue(input.forward());
    }
}
