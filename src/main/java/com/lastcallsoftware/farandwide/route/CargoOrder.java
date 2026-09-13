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
public record CargoOrder(UUID id, UUID playerId, int routeId, int originWaypointId, int destinationWaypointId,
        CargoStationBinding destinationStation, List<OrderLine> lines, RouteOperationResult activationResult) {
    public CargoOrder {
        Objects.requireNonNull(id);
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(destinationStation);
        Objects.requireNonNull(activationResult);
        lines = List.copyOf(lines);
        if (routeId <= 0 || originWaypointId <= 0 || destinationWaypointId <= 0
                || originWaypointId == destinationWaypointId || lines.isEmpty()
                || lines.size() > Constants.Orders.MAX_LINES
                || lines.stream().map((@NonNull OrderLine line) -> line.resource()).distinct().count() != lines.size()) {
            throw new IllegalArgumentException("Invalid order");
        }
    }

    public boolean delivered() {
        return lines.stream().allMatch(line -> line.remaining() == 0);
    }

    public int remaining(Identifier itemId) {
        return remaining(ItemResource.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(itemId)));
    }

    public int remaining(ItemResource resource) {
        return lines.stream().filter(line -> line.resource().equals(resource))
                .mapToInt((@NonNull OrderLine line) -> line.remaining()).sum();
    }

    public CargoOrder credit(Identifier itemId, int amount) {
        return credit(ItemResource.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(itemId)), amount);
    }

    public CargoOrder credit(ItemResource resource, int amount) {
        if (amount <= 0 || amount > remaining(resource)) {
            throw new IllegalArgumentException("Delivery credit exceeds remaining quantity");
        }
        return new CargoOrder(id, playerId, routeId, originWaypointId, destinationWaypointId, destinationStation,
                lines.stream().map(line -> line.resource().equals(resource)
                        ? new OrderLine(resource, line.requested(), line.delivered() + amount) : line).toList(), activationResult);
    }

    public CargoOrder withActivationResult(RouteOperationResult result) {
        return new CargoOrder(id, playerId, routeId, originWaypointId, destinationWaypointId, destinationStation, lines, result);
    }
}
