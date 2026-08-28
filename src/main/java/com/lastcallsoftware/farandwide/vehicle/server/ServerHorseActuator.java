package com.lastcallsoftware.farandwide.vehicle.server;

import com.lastcallsoftware.farandwide.vehicle.VehicleActuator;
import com.lastcallsoftware.farandwide.vehicle.HorseControls;
import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.vehicle.EquineVehicleSupport;
import com.lastcallsoftware.farandwide.vehicle.navigation.NavigationIntent;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.pathfinder.Path;

/** Uses vanilla equine pathfinding for horses, donkeys, and mules without a controlling client. */
final class ServerHorseActuator implements VehicleActuator {
    @Override
    public boolean supports(Entity vehicle) {
        return EquineVehicleSupport.supportsNavigation(vehicle);
    }

    @Override
    public boolean isAuthoritative(Entity vehicle) {
        return !vehicle.isClientAuthoritative();
    }

    @Override
    public void apply(Entity vehicle, NavigationIntent intent) {
        AbstractHorse horse = (AbstractHorse) vehicle;
        Path path = horse.getNavigation().getPath();
        if (!hasNextNode(path)
                || !path.getTarget().equals(net.minecraft.core.BlockPos.containing(intent.target()))) {
            path = horse.getNavigation().createPath(
                    intent.target().x, intent.target().y, intent.target().z, 0);
        }
        NavigationIntent steeringIntent = !hasNextNode(path)
                ? intent
                : NavigationIntent.toward(horse.position(), path.getNextEntityPos(horse));
        HorseControls.Input input = HorseControls.from(
                horse.getYRot(), steeringIntent, Constants.Vehicles.EQUINES_MOVE_WHILE_TURNING);
        horse.setYRot(input.yaw());
        horse.setYBodyRot(input.yaw());
        horse.setYHeadRot(input.yaw());
        if (input.forward() == 0.0F) {
            // Vanilla horse pathfinding cannot reliably rotate a stationary horse:
            // retaining a small path speed lets its move controller settle the heading
            // without the full moving-turn behavior used when the setting is enabled.
            if (hasNextNode(path)) {
                horse.getNavigation().moveTo(
                        path, Constants.Vehicles.EQUINE_MOVEMENT_SPEED_MODIFIER
                                * Constants.Vehicles.EQUINE_TURNING_SPEED_RATIO);
            }
            return;
        }
        if (hasNextNode(path)) {
            horse.getNavigation().moveTo(path, Constants.Vehicles.EQUINE_MOVEMENT_SPEED_MODIFIER);
        }
    }

    private static boolean hasNextNode(Path path) {
        return path != null
                && !path.isDone()
                && path.getNextNodeIndex() >= 0
                && path.getNextNodeIndex() < path.getNodeCount();
    }

    @Override
    public void stop(Entity vehicle) {
        AbstractHorse horse = (AbstractHorse) vehicle;
        horse.getNavigation().stop();
        horse.stopInPlace();
    }
}
