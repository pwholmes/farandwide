package com.lastcallsoftware.farandwide.route;

import com.lastcallsoftware.farandwide.Constants;
import java.util.List;
import org.eclipse.jdt.annotation.NonNullByDefault;

/** The player-selected route segments for an order before the server resolves their cargo stations. */
@NonNullByDefault
public record OrderJourney(List<Leg> legs) {
    public OrderJourney {
        legs = List.copyOf(legs);
        if (legs.isEmpty() || legs.size() > Constants.Orders.MAX_LEGS || legs.stream().distinct().count() != legs.size()) {
            throw new IllegalArgumentException("Invalid order journey");
        }
    }

    public record Leg(int routeId, int originWaypointId, int destinationWaypointId) {
        public Leg {
            if (routeId <= 0 || originWaypointId <= 0 || destinationWaypointId <= 0
                    || originWaypointId == destinationWaypointId) {
                throw new IllegalArgumentException("Invalid journey leg");
            }
        }
    }
}
