package com.lastcallsoftware.farandwide.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class WaypointTest {
    @Test
    void unallocatedWaypointDefaultsToNormalBehavior() {
        Waypoint waypoint = new Waypoint(Vec3.ZERO, Waypoint.DEFAULT_DIMENSION);

        assertEquals(0, waypoint.id());
        assertInstanceOf(WaypointAction.Normal.class, waypoint.action());
    }

    @Test
    void waypointCarriesItsStableIdentity() {
        Waypoint waypoint = new Waypoint(
                42, Vec3.ZERO, Waypoint.DEFAULT_DIMENSION, WaypointAction.normal());

        assertEquals(42, waypoint.id());
    }

    @Test
    void waypointCarriesAnIndividualArrivalRadius() {
        Waypoint waypoint = new Waypoint(
                42, Vec3.ZERO, Waypoint.DEFAULT_DIMENSION, WaypointAction.normal(), 6.5);

        assertEquals(6.5, waypoint.arrivalRadius());
        assertEquals(42.25, waypoint.arrivalRadiusSquared());
    }

    @Test
    void arrivalRadiusMustUseTheSupportedRangeAndIncrement() {
        assertThrows(IllegalArgumentException.class, () ->
                new Waypoint(1, Vec3.ZERO, Waypoint.DEFAULT_DIMENSION, WaypointAction.normal(), 0.5));
        assertThrows(IllegalArgumentException.class, () ->
                new Waypoint(1, Vec3.ZERO, Waypoint.DEFAULT_DIMENSION, WaypointAction.normal(), 3.25));
        assertThrows(IllegalArgumentException.class, () ->
                new Waypoint(1, Vec3.ZERO, Waypoint.DEFAULT_DIMENSION, WaypointAction.normal(), 16.5));
    }

    @Test
    void cargoActionKeepsIndependentFilters() {
        CargoBehavior behavior = new CargoBehavior(
                CargoOperation.UNLOAD_THEN_LOAD,
                CargoFilter.allowList(List.of(Identifier.parse("minecraft:coal"))),
                CargoFilter.allowList(List.of(Identifier.parse("minecraft:iron_ingot"))),
                Optional.of(new CargoStationBinding(new BlockPos(3, 64, -2), Direction.NORTH)),
                Optional.of(new CargoStationBinding(new BlockPos(4, 64, -2), Direction.SOUTH)));

        WaypointAction.Cargo cargo = WaypointAction.cargo(behavior);

        assertEquals(behavior, cargo.behavior());
        assertEquals(new BlockPos(3, 64, -2), cargo.behavior().loadStation().orElseThrow().position());
        assertEquals(new BlockPos(4, 64, -2), cargo.behavior().unloadStation().orElseThrow().position());
    }

    @Test
    void cargoBehaviorDetectsAnInventoryUsedInOppositeRoles() {
        CargoBehavior loading = new CargoBehavior(CargoOperation.LOAD, CargoFilter.all(), CargoFilter.all(),
                Optional.of(new CargoStationBinding(new BlockPos(3, 64, -2), Direction.NORTH)), Optional.empty());
        CargoBehavior unloadingSameInventory = new CargoBehavior(CargoOperation.UNLOAD, CargoFilter.all(), CargoFilter.all(),
                Optional.empty(), Optional.of(new CargoStationBinding(new BlockPos(3, 64, -2), Direction.SOUTH)));
        CargoBehavior loadingSameInventory = new CargoBehavior(CargoOperation.LOAD, CargoFilter.all(), CargoFilter.all(),
                Optional.of(new CargoStationBinding(new BlockPos(3, 64, -2), Direction.UP)), Optional.empty());

        assertTrue(loading.conflictsWithOppositeRole(unloadingSameInventory));
        assertFalse(loading.conflictsWithOppositeRole(loadingSameInventory));
    }

    @Test
    void stableIdsCannotBeNegative() {
        assertThrows(IllegalArgumentException.class, () ->
                new Waypoint(-1, Vec3.ZERO, Waypoint.DEFAULT_DIMENSION, WaypointAction.normal()));
    }
}
