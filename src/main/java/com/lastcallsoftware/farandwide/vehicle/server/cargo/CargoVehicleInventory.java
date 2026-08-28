package com.lastcallsoftware.farandwide.vehicle.server.cargo;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** Resolves usable cargo storage without coupling route traversal to entity internals. */
public final class CargoVehicleInventory {
    private static final List<CargoVehicleAdapter> ADAPTERS = List.of(
            new EquineCargoVehicleAdapter(),
            new ChestBoatCargoVehicleAdapter());

    private CargoVehicleInventory() {
    }

    public static Optional<ResourceHandler<ItemResource>> find(Entity entity) {
        return ADAPTERS.stream()
                .filter(adapter -> adapter.supports(entity))
                .findFirst()
                .flatMap(adapter -> adapter.cargoInventory(entity));
    }
}
