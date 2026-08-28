package com.lastcallsoftware.farandwide.vehicle.server.cargo;

import com.lastcallsoftware.farandwide.vehicle.BoatVehicleSupport;
import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractChestBoat;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;

/** Adapts all 27 inventory slots exposed by a Minecraft 26.2 chest boat. */
final class ChestBoatCargoVehicleAdapter implements CargoVehicleAdapter {
    @Override
    public boolean supports(Entity entity) {
        return BoatVehicleSupport.supportsCargoType(entity.getClass());
    }

    @Override
    public Optional<ResourceHandler<ItemResource>> cargoInventory(Entity entity) {
        return entity instanceof AbstractChestBoat chestBoat
                ? Optional.of(VanillaContainerWrapper.of(chestBoat))
                : Optional.empty();
    }
}
