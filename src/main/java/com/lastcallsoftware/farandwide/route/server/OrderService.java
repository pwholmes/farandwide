package com.lastcallsoftware.farandwide.route.server;

import com.lastcallsoftware.farandwide.Config;
import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.route.*;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideSavedData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/** Assembles orders on the server thread, then leaves transport to normal route assignments. */
@NonNullByDefault
public final class OrderService {
    private OrderService() {}

    public static Outcome place(ServerPlayer player, UUID id, int routeId, int originId, int destinationId,
            List<OrderLine> lines) {
        FarAndWideSavedData data = FarAndWideSavedData.get(player.level().getServer());
        CargoOrder previous = data.getOrder(id);
        if (previous != null) {
            return outcome(previous.playerId().equals(player.getUUID()) ? OrderResult.PLACED : OrderResult.INVALID_ORDER);
        }
        if (!validRequest(lines)) return outcome(OrderResult.INVALID_ORDER);
        if (data.getOrders().size() >= Constants.Orders.MAX_TRACKED_ORDERS) return outcome(OrderResult.TRACKING_FULL);
        Waypoint origin = data.getWaypoint(routeId, originId);
        Waypoint destination = data.getWaypoint(routeId, destinationId);
        if (!validEndpoints(origin, destination)) return outcome(OrderResult.INVALID_ENDPOINTS);
        CargoBehavior loading = ((WaypointAction.Cargo) origin.action()).behavior();
        CargoBehavior unloading = ((WaypointAction.Cargo) destination.action()).behavior();
        CargoStationBinding loadBinding = loading.loadStation().orElseThrow();
        CargoStationBinding unloadBinding = unloading.unloadStation().orElseThrow();
        if (loadBinding.position().equals(unloadBinding.position())) return outcome(OrderResult.INVALID_ENDPOINTS);
        Map<ItemResource, Integer> requested = new LinkedHashMap<>();
        for (OrderLine line : lines) {
            ItemResource resource = line.resource();
            if (resource.isEmpty()) return outcome(OrderResult.INVALID_ORDER);
            if (!loading.loadFilter().allows(line.itemId()) || !unloading.unloadFilter().allows(line.itemId())) {
                return new Outcome(OrderResult.FILTER_REJECTED, line.itemId().toString());
            }
            requested.put(resource, line.requested());
        }
        ServerLevel level = player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, origin.dimension()));
        if (level == null || !WaypointProximity.isWithinArrivalRadius(origin, loadBinding.position())) {
            return outcome(OrderResult.INVALID_ENDPOINTS);
        }
        List<CargoStationBinding> bindings = new ArrayList<>(loading.sourceInventories());
        for (CargoStationBinding source : bindings) {
            if (source.position().equals(loadBinding.position())
                    || source.position().equals(unloadBinding.position())
                    || !WaypointProximity.isWithinArrivalRadius(origin.position(), Constants.Orders.SOURCE_RADIUS, source.position())) {
                return outcome(OrderResult.INVALID_SOURCES);
            }
        }
        bindings.add(loadBinding);
        for (CargoStationBinding binding : bindings) {
            if (!CargoStationResolver.hasLoadedChunk(level, binding.position())) {
                if (Config.VEHICLE_CHUNK_RADIUS.get() == 0) return outcome(OrderResult.CHUNK_LOADING_DISABLED);
                // Only the bounded, explicitly linked storage chunks are needed during this synchronous operation.
                // No persistent chunk tickets are installed; ordinary unloading can reclaim them afterwards.
                level.getChunk(binding.position().getX() >> 4, binding.position().getZ() >> 4);
            }
        }
        List<ResourceHandler<ItemResource>> sources = new ArrayList<>();
        for (CargoStationBinding binding : loading.sourceInventories()) {
            var handler = CargoStationResolver.findInventory(level, binding).orElse(null);
            if (handler == null) return outcome(OrderResult.INVALID_SOURCES);
            sources.add(handler);
        }
        var loadStation = CargoStationResolver.findInventory(level, loadBinding).orElse(null);
        if (loadStation == null || sources.stream().anyMatch(source -> source == loadStation)) {
            return outcome(OrderResult.INVALID_SOURCES);
        }
        CargoOrder order = new CargoOrder(id, player.getUUID(), routeId, originId, destinationId,
                unloadBinding, lines, RouteOperationResult.SUCCESS);
        return fulfill(data, order, sources, loadStation, requested,
                () -> RouteService.activateCargoVehicleAssignmentsForOrder(player, routeId));
    }

    /** Lists exact item-and-component variants that can presently be extracted from linked source inventories. */
    public static List<AvailableItem> availableItems(ServerPlayer player, int routeId, int originId) {
        FarAndWideSavedData data = FarAndWideSavedData.get(player.level().getServer());
        Waypoint origin = data.getWaypoint(routeId, originId);
        if (!isOrigin(origin)) return List.of();
        CargoBehavior loading = ((WaypointAction.Cargo) origin.action()).behavior();
        ServerLevel level = player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, origin.dimension()));
        if (level == null) return List.of();

        Map<ItemResource, Integer> quantities = new LinkedHashMap<>();
        for (CargoStationBinding binding : loading.sourceInventories()) {
            CargoStationResolver.findInventory(level, binding).ifPresent(source -> {
                for (int slot = 0; slot < Math.min(source.size(), Constants.Cargo.MAX_SCANNED_SLOTS); slot++) {
                    ItemResource resource = source.getResource(slot);
                    if (resource.isEmpty()) continue;
                    int amount = source.getAmountAsInt(slot);
                    if (amount > 0) quantities.merge(resource, amount, Integer::sum);
                }
            });
        }
        return quantities.entrySet().stream()
                .map(entry -> new AvailableItem(entry.getKey(), entry.getValue()))
                .sorted(java.util.Comparator.comparing(item -> item.resource().getHoverName().getString()))
                .toList();
    }

    /** A retried accepted request is acknowledged without extracting more stock or reactivating its route. */
    static Outcome fulfill(FarAndWideSavedData data, CargoOrder order, List<ResourceHandler<ItemResource>> sources,
            ResourceHandler<ItemResource> loadStation, Map<ItemResource, Integer> requested,
            java.util.function.Supplier<RouteOperationResult> activate) {
        CargoOrder previous = data.getOrder(order.id());
        if (previous != null) return outcome(previous.playerId().equals(order.playerId()) ? OrderResult.PLACED : OrderResult.INVALID_ORDER);
        if (data.getOrders().size() >= Constants.Orders.MAX_TRACKED_ORDERS) return outcome(OrderResult.TRACKING_FULL);
        if (data.getWaypoint(order.routeId(), order.originWaypointId()) == null
                || data.getWaypoint(order.routeId(), order.destinationWaypointId()) == null) return outcome(OrderResult.INVALID_ENDPOINTS);
        Outcome assembled = assemble(sources, loadStation, requested);
        if (assembled.result() != OrderResult.PLACED) return assembled;
        data.addOrder(order);
        data.setOrderActivationResult(order.id(), activate.get());
        return outcome(OrderResult.PLACED);
    }

    public static boolean validRequest(List<OrderLine> lines) {
        return !lines.isEmpty() && lines.size() <= Constants.Orders.MAX_LINES
                && lines.stream().allMatch(line -> line.delivered() == 0)
                && lines.stream().map((@org.eclipse.jdt.annotation.NonNull OrderLine line) -> line.resource())
                        .distinct().count() == lines.size();
    }

    public static boolean isOrigin(@Nullable Waypoint waypoint) {
        return waypoint != null && waypoint.action() instanceof WaypointAction.Cargo cargo
                && cargo.behavior().operation() != CargoOperation.UNLOAD
                && cargo.behavior().loadStation().isPresent() && !cargo.behavior().sourceInventories().isEmpty();
    }

    public static boolean isDestination(@Nullable Waypoint waypoint) {
        return waypoint != null && waypoint.action() instanceof WaypointAction.Cargo cargo
                && cargo.behavior().operation() != CargoOperation.LOAD && cargo.behavior().unloadStation().isPresent();
    }

    static boolean validEndpoints(@Nullable Waypoint origin, @Nullable Waypoint destination) {
        return isOrigin(origin) && isDestination(destination) && origin.id() != destination.id()
                && origin.dimension().equals(destination.dimension());
    }

    /** Extract everything before inserting anything, so aliases cannot recycle newly staged items into the order. */
    static Outcome assemble(List<ResourceHandler<ItemResource>> sources, ResourceHandler<ItemResource> destination,
            Map<ItemResource, Integer> requested) {
        try (Transaction transaction = Transaction.openRoot()) {
            for (var entry : requested.entrySet()) {
                int remaining = entry.getValue();
                for (var source : sources) {
                    for (int slot = 0; slot < Math.min(source.size(), Constants.Cargo.MAX_SCANNED_SLOTS) && remaining > 0; slot++) {
                        remaining -= source.extract(slot, entry.getKey(), remaining, transaction);
                    }
                }
                if (remaining > 0) return new Outcome(OrderResult.INSUFFICIENT_STOCK,
                        BuiltInRegistries.ITEM.getKey(entry.getKey().getItem()).toString());
            }
            for (var entry : requested.entrySet()) {
                int remaining = entry.getValue();
                for (int slot = 0; slot < Math.min(destination.size(), Constants.Cargo.MAX_SCANNED_SLOTS) && remaining > 0; slot++) {
                    remaining -= destination.insert(slot, entry.getKey(), remaining, transaction);
                }
                if (remaining > 0) return outcome(OrderResult.LOAD_STATION_FULL);
            }
            transaction.commit();
        }
        return outcome(OrderResult.PLACED);
    }

    public static Outcome cancel(ServerPlayer player, UUID orderId) {
        return outcome(FarAndWideSavedData.get(player.level().getServer()).cancelOrder(orderId, player.getUUID())
                ? OrderResult.CANCELLED : OrderResult.NOT_FOUND);
    }

    private static Outcome outcome(OrderResult result) {
        return new Outcome(result, "");
    }

    public record Outcome(OrderResult result, String detail) {}

    public record AvailableItem(ItemResource resource, int quantity) {}
}
