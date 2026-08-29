package com.lastcallsoftware.farandwide.route;

import com.lastcallsoftware.farandwide.Constants;
import java.util.Objects;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** A stable, server-owned point in a route and the action performed there. */
public record Waypoint(int id, Vec3 position, Identifier dimension, WaypointAction action, double arrivalRadius) {
    public static final Identifier DEFAULT_DIMENSION = Identifier.withDefaultNamespace("overworld");

    public Waypoint {
        if (id < 0) {
            throw new IllegalArgumentException("Waypoint ID cannot be negative");
        }
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(action, "action");
        if (!isValidArrivalRadius(arrivalRadius)) {
            throw new IllegalArgumentException("Waypoint arrival radius is outside the allowed range");
        }
    }

    public Waypoint(int id, Vec3 position, Identifier dimension, WaypointAction action) {
        this(id, position, dimension, action, Constants.Waypoints.DEFAULT_ARRIVAL_RADIUS);
    }

    /** Creates an unallocated normal waypoint for submission to server-owned storage. */
    public Waypoint(Vec3 position, Identifier dimension) {
        this(0, position, dimension, WaypointAction.normal());
    }

    /** Kept for existing callers; new persisted waypoints should provide their dimension. */
    public Waypoint(Vec3 position) {
        this(position, DEFAULT_DIMENSION);
    }

    public Waypoint withId(int allocatedId) {
        return new Waypoint(allocatedId, position, dimension, action, arrivalRadius);
    }

    public double arrivalRadiusSquared() {
        return arrivalRadius * arrivalRadius;
    }

    /** Uses the same three-dimensional arrival check on both logical sides. */
    public boolean hasArrived(Vec3 entityPosition) {
        return position.distanceToSqr(entityPosition) <= arrivalRadiusSquared();
    }

    public static boolean isValidArrivalRadius(double radius) {
        return Double.isFinite(radius)
                && radius >= Constants.Waypoints.MIN_ARRIVAL_RADIUS
                && radius <= Constants.Waypoints.MAX_ARRIVAL_RADIUS
                && Math.abs((radius - Constants.Waypoints.MIN_ARRIVAL_RADIUS)
                        / Constants.Waypoints.ARRIVAL_RADIUS_STEP
                        - Math.rint((radius - Constants.Waypoints.MIN_ARRIVAL_RADIUS)
                                / Constants.Waypoints.ARRIVAL_RADIUS_STEP)) < 0.000001;
    }
}
