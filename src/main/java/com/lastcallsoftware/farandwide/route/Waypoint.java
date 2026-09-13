package com.lastcallsoftware.farandwide.route;

import com.lastcallsoftware.farandwide.Constants;
import java.util.Objects;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.eclipse.jdt.annotation.NonNullByDefault;

/** A stable, server-owned point in a route and the action performed there. */
@NonNullByDefault
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

    /** Uses a three-dimensional point check where no entity bounds are available. */
    public boolean hasArrived(Vec3 entityPosition) {
        return position.distanceToSqr(entityPosition) <= arrivalRadiusSquared();
    }

    /**
     * Measures arrival from the vehicle's physical bounds. This keeps large
     * vehicles from stalling when pathfinding stops their center just outside a
     * small waypoint radius even though the vehicle itself has reached it.
     */
    public boolean hasArrived(Entity entity) {
        return hasArrived(entity.getBoundingBox());
    }

    boolean hasArrived(AABB entityBounds) {
        double dx = Math.max(entityBounds.minX - position.x, Math.max(0.0, position.x - entityBounds.maxX));
        double dy = Math.max(entityBounds.minY - position.y, Math.max(0.0, position.y - entityBounds.maxY));
        double dz = Math.max(entityBounds.minZ - position.z, Math.max(0.0, position.z - entityBounds.maxZ));
        return dx * dx + dy * dy + dz * dz <= arrivalRadiusSquared();
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
