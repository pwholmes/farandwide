package com.lastcallsoftware.farandwide.vehicle.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ServerMinecartActuatorTest {
    @Test
    void startsTowardTheTargetOnStraightAndCurvedRails() {
        Vec3 east = ServerMinecartActuator.nextMovement(
                Vec3.ZERO, Vec3.ZERO, new Vec3(10, 0, 0), RailShape.EAST_WEST);
        Vec3 north = ServerMinecartActuator.nextMovement(
                Vec3.ZERO, Vec3.ZERO, new Vec3(0, 0, -10), RailShape.NORTH_EAST);

        assertTrue(east.x > 0.0);
        assertEquals(0.0, east.z);
        assertTrue(north.z < 0.0);
        assertEquals(0.0, north.x);
    }

    @Test
    void gentlePushDoesNotCapPoweredRailSpeed() {
        Vec3 slow = ServerMinecartActuator.nextMovement(
                new Vec3(0.05, 0.02, 0.0), Vec3.ZERO, new Vec3(10, 0, 0), RailShape.EAST_WEST);
        Vec3 boosted = new Vec3(0.3, 0.02, 0.0);

        assertTrue(slow.x > 0.05 && slow.x < 0.1);
        assertEquals(0.02, slow.y);
        assertEquals(boosted, ServerMinecartActuator.nextMovement(
                boosted, Vec3.ZERO, new Vec3(10, 0, 0), RailShape.EAST_WEST));
    }

    @Test
    void stoppedCartCanReverseTowardTheNextWaypoint() {
        Vec3 reversed = ServerMinecartActuator.nextMovement(
                Vec3.ZERO, Vec3.ZERO, new Vec3(-10, 0, 0), RailShape.EAST_WEST);
        Vec3 redirected = ServerMinecartActuator.nextMovement(
                new Vec3(0.3, 0, 0), Vec3.ZERO, new Vec3(-10, 0, 0), RailShape.EAST_WEST);

        assertTrue(reversed.x < 0.0);
        assertTrue(redirected.x < 0.0);
    }
}
