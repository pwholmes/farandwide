package com.lastcallsoftware.farandwide.route;

import com.lastcallsoftware.farandwide.Constants;
import java.util.List;
import java.util.Objects;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;

/** One explicitly connected portion of an order journey, including its committed unload receipts. */
@NonNullByDefault
public record OrderLeg(int routeId, int originWaypointId, int destinationWaypointId,
        CargoStationBinding destinationStation, List<OrderLine> lines, RouteOperationResult activationResult) {
    public OrderLeg {
        Objects.requireNonNull(destinationStation);
        Objects.requireNonNull(activationResult);
        lines = List.copyOf(lines);
        if (routeId <= 0 || originWaypointId <= 0 || destinationWaypointId <= 0
                || originWaypointId == destinationWaypointId || lines.isEmpty()
                || lines.size() > Constants.Orders.MAX_LINES
                || lines.stream().map((@NonNull OrderLine line) -> line.resource()).distinct().count() != lines.size()) {
            throw new IllegalArgumentException("Invalid order leg");
        }
    }

    public int remaining(ItemResource resource) {
        return lines.stream().filter(line -> line.resource().equals(resource))
                .mapToInt((@NonNull OrderLine line) -> line.remaining()).sum();
    }

    public int delivered(ItemResource resource) {
        return lines.stream().filter(line -> line.resource().equals(resource))
                .mapToInt((@NonNull OrderLine line) -> line.delivered()).sum();
    }

    public OrderLeg credit(ItemResource resource, int amount) {
        if (amount <= 0 || amount > remaining(resource)) {
            throw new IllegalArgumentException("Delivery credit exceeds remaining quantity");
        }
        return new OrderLeg(routeId, originWaypointId, destinationWaypointId, destinationStation,
                lines.stream().map(line -> line.resource().equals(resource)
                        ? new OrderLine(resource, line.requested(), line.delivered() + amount) : line).toList(), activationResult);
    }

    public OrderLeg withActivationResult(RouteOperationResult result) {
        return new OrderLeg(routeId, originWaypointId, destinationWaypointId, destinationStation, lines, result);
    }
}
