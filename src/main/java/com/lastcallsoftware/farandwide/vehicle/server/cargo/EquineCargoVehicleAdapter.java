package com.lastcallsoftware.farandwide.vehicle.server.cargo;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.vehicle.EquineVehicleSupport;

import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.neoforged.neoforge.transfer.RangedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;

/** Exposes only chest cargo slots on chested donkeys and mules. */
final class EquineCargoVehicleAdapter implements CargoVehicleAdapter {
    @Override
    public boolean supports(Entity entity) {
        return EquineVehicleSupport.supportsCargoType(entity.getClass());
    }

    @Override
    public Optional<ResourceHandler<ItemResource>> cargoInventory(Entity entity) {
        if (!(entity instanceof AbstractChestedHorse equine)
                || !EquineVehicleSupport.hasCargoStorage(entity.getClass(), equine.hasChest())) {
            return Optional.empty();
        }
        ResourceHandler<ItemResource> inventory = VanillaContainerWrapper.of(equine.getInventory());
        int cargoSlots = inventory.size() - Constants.Cargo.EQUINE_EQUIPMENT_SLOT_COUNT;
        return cargoSlots <= 0
                ? Optional.empty()
                : Optional.of(RangedResourceHandler.of(
                        inventory, Constants.Cargo.EQUINE_EQUIPMENT_SLOT_COUNT, inventory.size()));
    }
}
