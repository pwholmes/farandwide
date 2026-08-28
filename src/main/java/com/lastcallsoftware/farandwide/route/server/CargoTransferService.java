package com.lastcallsoftware.farandwide.route.server;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.route.CargoBehavior;
import com.lastcallsoftware.farandwide.route.CargoFilter;
import com.lastcallsoftware.farandwide.route.CargoOperation;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Bounded, transactional item movement between station and vehicle cargo handlers. */
public final class CargoTransferService {
    private CargoTransferService() {
    }

    public static TransferResult transfer(ResourceHandler<ItemResource> vehicle,
            Optional<ResourceHandler<ItemResource>> loadStation,
            Optional<ResourceHandler<ItemResource>> unloadStation, CargoBehavior behavior) {
        return transferResources(vehicle, loadStation, unloadStation, behavior.operation(),
                resource -> matches(behavior.loadFilter(), resource),
                resource -> matches(behavior.unloadFilter(), resource));
    }

    static <T extends Resource> TransferResult transferResources(ResourceHandler<T> vehicle,
            Optional<ResourceHandler<T>> loadStation, Optional<ResourceHandler<T>> unloadStation,
            CargoOperation operation, Predicate<T> loadFilter, Predicate<T> unloadFilter) {
        int unloaded = 0;
        int loaded = 0;
        if (operation == CargoOperation.UNLOAD_THEN_LOAD) {
            unloaded = unloadStation.map(station -> transfer(
                    vehicle, station, unloadFilter)).orElse(0);
            loaded = loadStation.map(station -> transfer(
                    station, vehicle, loadFilter)).orElse(0);
        } else if (operation == CargoOperation.UNLOAD) {
            unloaded = unloadStation.map(station -> transfer(
                    vehicle, station, unloadFilter)).orElse(0);
        } else if (operation == CargoOperation.LOAD) {
            loaded = loadStation.map(station -> transfer(
                    station, vehicle, loadFilter)).orElse(0);
        }
        return new TransferResult(unloaded, loaded);
    }

    /**
     * Moves at most one source stack, returning the quantity actually moved.
     * A zero result means that no eligible source stack can currently be moved.
     */
    static <T extends Resource> int transferOneStack(ResourceHandler<T> source, ResourceHandler<T> destination,
            Predicate<T> filter) {
        int sourceSlots = Math.min(source.size(), Constants.Cargo.MAX_SCANNED_SLOTS);
        int destinationSlots = Math.min(destination.size(), Constants.Cargo.MAX_SCANNED_SLOTS);
        for (int sourceSlot = 0; sourceSlot < sourceSlots; sourceSlot++) {
            T resource = source.getResource(sourceSlot);
            if (resource.isEmpty() || !filter.test(resource)) {
                continue;
            }
            int available = Math.min(source.getAmountAsInt(sourceSlot), Constants.Cargo.MAX_ITEMS_PER_STACK);
            if (available <= 0) {
                continue;
            }
            int movedTotal = 0;
            for (int destinationSlot = 0; destinationSlot < destinationSlots && available > 0; destinationSlot++) {
                int moved = moveTransactional(source, sourceSlot, destination, destinationSlot, resource, available);
                available -= moved;
                movedTotal += moved;
            }
            if (movedTotal > 0) {
                return movedTotal;
            }
        }
        return 0;
    }

    static <T extends Resource> TransferResult transferResources(ResourceHandler<T> vehicle,
            ResourceHandler<T> station, CargoOperation operation,
            Predicate<T> loadFilter, Predicate<T> unloadFilter) {
        return transferResources(vehicle, Optional.of(station), Optional.of(station),
                operation, loadFilter, unloadFilter);
    }

    private static <T extends Resource> int transfer(ResourceHandler<T> source,
            ResourceHandler<T> destination, Predicate<T> filter) {
        int movedTotal = 0;
        int sourceSlots = Math.min(source.size(), Constants.Cargo.MAX_SCANNED_SLOTS);
        int destinationSlots = Math.min(destination.size(), Constants.Cargo.MAX_SCANNED_SLOTS);

        for (int sourceSlot = 0; sourceSlot < sourceSlots && movedTotal < Constants.Cargo.MAX_ITEMS_PER_OPERATION; sourceSlot++) {
            T resource = source.getResource(sourceSlot);
            if (resource.isEmpty() || !filter.test(resource)) {
                continue;
            }
            int available = Math.min(source.getAmountAsInt(sourceSlot), Constants.Cargo.MAX_ITEMS_PER_OPERATION - movedTotal);

            for (int destinationSlot = 0; destinationSlot < destinationSlots && available > 0; destinationSlot++) {
                int moved = moveTransactional(
                        source, sourceSlot, destination, destinationSlot, resource, available);
                available -= moved;
                movedTotal += moved;
            }
        }
        return movedTotal;
    }

    private static <T extends Resource> int moveTransactional(ResourceHandler<T> source, int sourceSlot,
            ResourceHandler<T> destination, int destinationSlot, T resource, int requested) {
        int candidate;
        try (Transaction simulation = Transaction.openRoot()) {
            int extracted = source.extract(sourceSlot, resource, requested, simulation);
            candidate = destination.insert(destinationSlot, resource, extracted, simulation);
        }
        if (candidate <= 0) {
            return 0;
        }

        try (Transaction transaction = Transaction.openRoot()) {
            int extracted = source.extract(sourceSlot, resource, candidate, transaction);
            int inserted = destination.insert(destinationSlot, resource, extracted, transaction);
            if (extracted == candidate && inserted == candidate) {
                transaction.commit();
                return candidate;
            }
        }
        return 0;
    }

    static boolean matches(CargoFilter filter, ItemResource resource) {
        return filter.mode() == CargoFilter.Mode.ALL
                || filter.itemIds().contains(BuiltInRegistries.ITEM.getKey(resource.getItem()));
    }

    public record TransferResult(int unloaded, int loaded) {
        public int totalMoved() {
            return unloaded + loaded;
        }
    }
}
