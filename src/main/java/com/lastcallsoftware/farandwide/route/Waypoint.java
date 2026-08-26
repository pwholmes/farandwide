package com.lastcallsoftware.farandwide.route;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** A point in a route, including the dimension in which it is valid. */
public record Waypoint(Vec3 position, Identifier dimension) {
    public static final Identifier DEFAULT_DIMENSION = Identifier.withDefaultNamespace("overworld");

    /** Kept for existing callers; new persisted waypoints should provide their dimension. */
    public Waypoint(Vec3 position) {
        this(position, DEFAULT_DIMENSION);
    }
}
