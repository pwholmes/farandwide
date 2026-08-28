package com.lastcallsoftware.farandwide.route.server;

import com.lastcallsoftware.farandwide.route.CargoStationBinding;
import com.lastcallsoftware.farandwide.route.Waypoint;
import com.lastcallsoftware.farandwide.route.WaypointProximity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

/** Resolves the one explicitly bound inventory for a cargo waypoint. */
public final class CargoStationResolver {
    private CargoStationResolver() {
    }

    public static Optional<ResourceHandler<ItemResource>> find(
            ServerLevel level, Waypoint waypoint, Optional<CargoStationBinding> station) {
        return find(level, waypoint.position(), waypoint.arrivalRadius(), station);
    }

    static Optional<ResourceHandler<ItemResource>> find(ServerLevel level,
            net.minecraft.world.phys.Vec3 waypointPosition, double arrivalRadius,
            Optional<CargoStationBinding> station) {
        return station
                .filter(binding -> WaypointProximity.isWithinArrivalRadius(
                        waypointPosition, arrivalRadius, binding.position()))
                .filter(binding -> hasLoadedChunk(level, binding.position()))
                .flatMap(binding -> findInventory(level, binding));
    }

    private static Optional<ResourceHandler<ItemResource>> findInventory(
            ServerLevel level, CargoStationBinding binding) {
        ResourceHandler<ItemResource> handler = level.getCapability(
                Capabilities.Item.BLOCK, binding.position(), binding.accessSide());
        if (handler != null) {
            return Optional.of(handler);
        }

        BlockEntity blockEntity = level.getBlockEntity(binding.position());
        if (blockEntity instanceof Container container) {
            return Optional.of(wrapVanillaContainer(container, binding.accessSide()));
        }
        return Optional.empty();
    }

    /** Adapts vanilla inventories that do not publish an item capability, such as chests. */
    static ResourceHandler<ItemResource> wrapVanillaContainer(
            Container container, net.minecraft.core.Direction accessSide) {
        return container instanceof WorldlyContainer worldly
                ? new WorldlyContainerWrapper(worldly, accessSide)
                : VanillaContainerWrapper.of(container);
    }

    static boolean isWithinArrivalRadius(Waypoint waypoint, CargoStationBinding binding) {
        return WaypointProximity.isWithinArrivalRadius(waypoint, binding.position());
    }

    static boolean hasLoadedChunk(ServerLevel level, BlockPos position) {
        return level.hasChunk(
                SectionPos.blockToSectionCoord(position.getX()),
                SectionPos.blockToSectionCoord(position.getZ()));
    }
}
