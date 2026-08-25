package com.lastcallsoftware.farandwide.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Comparator;

import com.lastcallsoftware.farandwide.FarAndWide;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public class Route {
    private static final double REMOVE_RADIUS = 5.0;
    private static final double OFFSET_DISTANCE = 1.0;
    private static int nextId = 1; // Static variable to keep track of the next available ID

    private final int id;
    private String name;
    private final List<Waypoint> waypoints = new ArrayList<>();
    private boolean active = false;

    public Route() {
        this.id = nextId++; // Assign the next available ID and increment the counter
        this.name = "New Route"; // Default name for a new route
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void addWaypoint(Waypoint waypoint) {
        waypoints.add(waypoint);

        FarAndWide.LOGGER.info("Waypoint added at {}", waypoint.position());
    }

    public void removeWaypoint(Waypoint waypoint) {
        waypoints.remove(waypoint);

        FarAndWide.LOGGER.info("Waypoint removed at {}", waypoint.position());
    }

    public Waypoint getNearestWaypoint() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return null;
        }
        Vec3 playerPosition = minecraft.player.position();
        return getNearestWaypoint(playerPosition);
    }

    public Waypoint getNearestWaypoint(Vec3 position) {
        return getNearestWaypoint(position, REMOVE_RADIUS);
    }

    public Waypoint getNearestWaypoint(Vec3 position, double maxDistance) {
        double maxDistanceSquared = maxDistance * maxDistance;
        return waypoints.stream()
                .filter(waypoint -> waypoint.position().distanceToSqr(position) <= maxDistanceSquared)
                .min(Comparator.comparingDouble(waypoint -> waypoint.position().distanceToSqr(position)))
                .orElse(null);
    }

    public void addCurrentPosition() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        Vec3 look = minecraft.player.getLookAngle();
        Vec3 inFront = minecraft.player.position().add(
                look.x * OFFSET_DISTANCE, 0, look.z * OFFSET_DISTANCE);
        addWaypoint(new Waypoint(inFront));
    }

    public void removeCurrentPosition() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        Vec3 playerPosition = minecraft.player.position();
        double radiusSquared = REMOVE_RADIUS * REMOVE_RADIUS;

        Waypoint closestWaypoint = waypoints.stream()
                .filter(waypoint -> waypoint.position().distanceToSqr(playerPosition) <= radiusSquared)
                .min(Comparator.comparingDouble(waypoint -> waypoint.position().distanceToSqr(playerPosition)))
                .orElse(null);
        if (closestWaypoint != null) {
            removeWaypoint(closestWaypoint);
        }
    }

    public void toggleCurrentPosition() {
        Waypoint nearest = getNearestWaypoint();
        if (nearest != null) {
            removeWaypoint(nearest);
        } else {
            addCurrentPosition();
        }
    }

    public List<Waypoint> getWaypoints() {
        return Collections.unmodifiableList(waypoints);
    }

    public void clear() {
        waypoints.clear();
    }

    public void toggleActive() {
        this.active = !this.active;
    }

    public boolean isActive() {
        return active;
    }

}