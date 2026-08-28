package com.lastcallsoftware.farandwide.vehicle.server.cargo;

import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** Server-side adapter separating cargo inventory access from movement actuation. */
public interface CargoVehicleAdapter {
    boolean supports(Entity entity);

    Optional<ResourceHandler<ItemResource>> cargoInventory(Entity entity);
}
