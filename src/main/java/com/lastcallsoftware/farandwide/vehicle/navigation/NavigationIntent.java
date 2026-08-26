package com.lastcallsoftware.farandwide.vehicle.navigation;

import net.minecraft.world.phys.Vec3;

/** Temporary movement intent for one tick; never persisted as route state. */
public record NavigationIntent(Vec3 target, Vec3 direction, double distance) {
    public static NavigationIntent toward(Vec3 position, Vec3 target) {
        Vec3 delta = target.subtract(position);
        double distance = delta.length();
        Vec3 direction = distance == 0.0 ? Vec3.ZERO : delta.scale(1.0 / distance);
        return new NavigationIntent(target, direction, distance);
    }
}
