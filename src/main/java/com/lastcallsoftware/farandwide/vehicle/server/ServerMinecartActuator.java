package com.lastcallsoftware.farandwide.vehicle.server;

import com.lastcallsoftware.farandwide.vehicle.VehicleActuator;
import com.lastcallsoftware.farandwide.vehicle.navigation.NavigationIntent;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

/** Adds gentle propulsion while vanilla minecart physics follows the rails. */
final class ServerMinecartActuator implements VehicleActuator {
    private static final double CRUISING_SPEED = 0.1;
    private static final double ACCELERATION = 0.01;

    @Override
    public boolean supports(Entity vehicle) {
        return vehicle instanceof AbstractMinecart;
    }

    @Override
    public boolean isAuthoritative(Entity vehicle) {
        return !vehicle.level().isClientSide();
    }

    @Override
    public void apply(Entity vehicle, NavigationIntent intent) {
        AbstractMinecart cart = (AbstractMinecart) vehicle;
        BlockPos railPos = cart.getCurrentBlockPosOrRailBelow();
        BlockState state = cart.level().getBlockState(railPos);
        if (!(state.getBlock() instanceof BaseRailBlock rail)) {
            return;
        }
        // An unpowered powered rail is a vanilla brake, including for routed carts.
        if (rail instanceof PoweredRailBlock poweredRail && !poweredRail.isActivatorRail()
                && !state.getValue(PoweredRailBlock.POWERED)) {
            return;
        }

        RailShape shape = rail.getRailDirection(state, cart.level(), railPos, cart);
        cart.setDeltaMovement(nextMovement(cart.getDeltaMovement(), cart.position(), intent.target(), shape));
    }

    static Vec3 nextMovement(Vec3 movement, Vec3 position, Vec3 target, RailShape shape) {
        Vec3 horizontal = new Vec3(movement.x, 0.0, movement.z);
        double speed = horizontal.length();
        Vec3 towardTarget = initialDirection(position, target, shape);
        if (isStraight(shape) && horizontal.dot(towardTarget) < -1.0E-4) {
            // A cart already rolling the wrong way must be able to start a reversed route.
            return towardTarget.scale(ACCELERATION).add(0.0, movement.y, 0.0);
        }
        if (speed >= CRUISING_SPEED) {
            return movement;
        }
        Vec3 direction = speed > 1.0E-4
                ? horizontal.scale(1.0 / speed)
                : towardTarget;
        if (direction.lengthSqr() == 0.0) {
            return movement;
        }
        return direction.scale(Math.min(CRUISING_SPEED, speed + ACCELERATION))
                .add(0.0, movement.y, 0.0);
    }

    private static boolean isStraight(RailShape shape) {
        return switch (shape) {
            case NORTH_SOUTH, EAST_WEST, ASCENDING_NORTH, ASCENDING_SOUTH,
                    ASCENDING_EAST, ASCENDING_WEST -> true;
            default -> false;
        };
    }

    private static Vec3 initialDirection(Vec3 position, Vec3 target, RailShape shape) {
        Pair<Vec3i, Vec3i> exits = AbstractMinecart.exits(shape);
        Vec3i first = exits.getFirst();
        Vec3i second = exits.getSecond();
        Vec3 towardTarget = target.subtract(position);
        double firstScore = towardTarget.x * first.getX() + towardTarget.z * first.getZ();
        double secondScore = towardTarget.x * second.getX() + towardTarget.z * second.getZ();
        Vec3i exit = firstScore > secondScore ? first : second;
        Vec3 horizontal = new Vec3(exit.getX(), 0.0, exit.getZ());
        return horizontal.lengthSqr() == 0.0 ? Vec3.ZERO : horizontal.normalize();
    }

    @Override
    public void stop(Entity vehicle) {
        vehicle.setDeltaMovement(Vec3.ZERO);
    }
}
