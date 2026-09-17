package com.lastcallsoftware.farandwide.route.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lastcallsoftware.farandwide.route.CargoStationBinding;
import com.lastcallsoftware.farandwide.route.Waypoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class CargoStationResolverTest {
    @Test
    void wrapsVanillaContainersThatDoNotExposeAnItemCapability() {
        assertTrue(CargoStationResolver.wrapVanillaContainer(new SimpleContainer(3), Direction.UP).size() == 3);
    }

    @Test
    void requiresTheExplicitStationToBeWithinTheFixedStationRadius() {
        Waypoint waypoint = new Waypoint(
                1, new Vec3(10.5, 64.5, -2.5), Identifier.parse("minecraft:overworld"),
                com.lastcallsoftware.farandwide.route.WaypointAction.normal());

        assertTrue(CargoStationResolver.isWithinStationRadius(
                waypoint, new CargoStationBinding(new BlockPos(13, 64, -3), Direction.UP)));
        assertFalse(CargoStationResolver.isWithinStationRadius(
                waypoint, new CargoStationBinding(new BlockPos(19, 64, -3), Direction.UP)));

        Waypoint widerWaypoint = new Waypoint(
                2, waypoint.position(), waypoint.dimension(), waypoint.action(), 5.0);
        assertFalse(CargoStationResolver.isWithinStationRadius(
                widerWaypoint, new CargoStationBinding(new BlockPos(19, 64, -3), Direction.UP)));
    }
}
