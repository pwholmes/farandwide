package com.lastcallsoftware.farandwide.vehicle.server.cargo;

import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;

/** Exposes vanilla chest and hopper minecart storage to cargo waypoints. */
final class MinecartCargoVehicleAdapter implements CargoVehicleAdapter {
    @Override
    public boolean supports(Entity entity) {
        return entity instanceof AbstractMinecartContainer;
    }

    @Override
    public Optional<ResourceHandler<ItemResource>> cargoInventory(Entity entity) {
        return entity instanceof AbstractMinecartContainer container
                ? Optional.of(VanillaContainerWrapper.of(container))
                : Optional.empty();
    }
}
