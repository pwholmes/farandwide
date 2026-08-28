package com.lastcallsoftware.farandwide.route;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Shared spatial rules for waypoint arrival and cargo-station binding. */
public final class WaypointProximity {
    private WaypointProximity() {
    }

    /** Returns whether an explicit block inventory is close enough to a waypoint. */
    public static boolean isWithinArrivalRadius(Waypoint waypoint, BlockPos inventoryPosition) {
        return isWithinArrivalRadius(waypoint.position(), waypoint.arrivalRadius(), inventoryPosition);
    }

    public static boolean isWithinArrivalRadius(Vec3 waypointPosition, double arrivalRadius, BlockPos inventoryPosition) {
        return waypointPosition.distanceToSqr(Vec3.atCenterOf(inventoryPosition))
                <= arrivalRadius * arrivalRadius;
    }
}
