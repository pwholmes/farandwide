package com.lastcallsoftware.farandwide.route;

import com.lastcallsoftware.farandwide.Constants;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;

/** A tracking record; it neither owns cargo nor reserves or controls any vehicle. */
@NonNullByDefault
public record CargoOrder(UUID id, UUID playerId, List<OrderLeg> legs) {
    public CargoOrder {
        Objects.requireNonNull(id);
        Objects.requireNonNull(playerId);
        legs = List.copyOf(legs);
        if (legs.isEmpty() || legs.size() > Constants.Orders.MAX_LEGS) {
            throw new IllegalArgumentException("Invalid order");
        }
        List<OrderLine> firstLegLines = legs.getFirst().lines();
        List<ItemResource> requestedResources = firstLegLines.stream().map((@NonNull OrderLine line) -> line.resource()).toList();
        if (legs.stream().skip(1).anyMatch(leg -> !requestedResources.equals(
                leg.lines().stream().map((@NonNull OrderLine line) -> line.resource()).toList())
                || leg.lines().stream().anyMatch(line -> line.requested() != firstLegLines.get(
                        requestedResources.indexOf(line.resource())).requested()))) {
            throw new IllegalArgumentException("Order legs must carry the same requested items");
        }
    }

    /** Compatibility constructor for existing single-leg callers and persisted data. */
    public CargoOrder(UUID id, UUID playerId, int routeId, int originWaypointId, int destinationWaypointId,
            CargoStationBinding destinationStation, List<OrderLine> lines, RouteOperationResult activationResult) {
        this(id, playerId, List.of(new OrderLeg(routeId, originWaypointId, destinationWaypointId,
                destinationStation, lines, activationResult)));
    }

    /** The final leg is the externally visible delivery record. */
    private OrderLeg finalLeg() { return legs.getLast(); }

    /** Legacy accessors keep existing single-leg callers source-compatible during the itinerary migration. */
    public int routeId() { return finalLeg().routeId(); }
    public int originWaypointId() { return legs.getFirst().originWaypointId(); }
    public int destinationWaypointId() { return finalLeg().destinationWaypointId(); }
    public CargoStationBinding destinationStation() { return finalLeg().destinationStation(); }
    public List<OrderLine> lines() { return finalLeg().lines(); }
    public RouteOperationResult activationResult() { return finalLeg().activationResult(); }

    public boolean delivered() {
        return finalLeg().lines().stream().allMatch(line -> line.remaining() == 0);
    }

    public int remaining(Identifier itemId) {
        return remaining(ItemResource.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(itemId)));
    }

    public int remaining(ItemResource resource) {
        return finalLeg().remaining(resource);
    }

    public CargoOrder credit(Identifier itemId, int amount) {
        return credit(ItemResource.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(itemId)), amount);
    }

    public CargoOrder credit(ItemResource resource, int amount) {
        if (amount <= 0 || amount > remaining(resource)) {
            throw new IllegalArgumentException("Delivery credit exceeds remaining quantity");
        }
        return creditLeg(legs.size() - 1, resource, amount);
    }

    public CargoOrder withActivationResult(RouteOperationResult result) {
        return withLegActivationResult(legs.size() - 1, result);
    }

    public CargoOrder creditLeg(int legIndex, ItemResource resource, int amount) {
        if (legIndex < 0 || legIndex >= legs.size()) throw new IllegalArgumentException("Invalid order leg index");
        List<OrderLeg> updated = new java.util.ArrayList<>(legs);
        updated.set(legIndex, legs.get(legIndex).credit(resource, amount));
        return new CargoOrder(id, playerId, updated);
    }

    public CargoOrder withLegActivationResult(int legIndex, RouteOperationResult result) {
        if (legIndex < 0 || legIndex >= legs.size()) throw new IllegalArgumentException("Invalid order leg index");
        List<OrderLeg> updated = new java.util.ArrayList<>(legs);
        updated.set(legIndex, legs.get(legIndex).withActivationResult(result));
        return new CargoOrder(id, playerId, updated);
    }
}
