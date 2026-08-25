package com.lastcallsoftware.farandwide.route;

public class RouteAssignment {
    private final int routeId;
    private final int vehicleId;
    private int targetWaypointIndex;
    private boolean active;

    public RouteAssignment(int routeId, int vehicleId, int targetWaypointIndex) {
        this.routeId = routeId;
        this.vehicleId = vehicleId;
        this.targetWaypointIndex = targetWaypointIndex;
        this.active = true;
    }

    public int getRouteId() {
        return routeId;
    }

    public int getVehicleId() {
        return vehicleId;
    }

    public int getTargetWaypointIndex() {
        return targetWaypointIndex;
    }

    public void setTargetWaypointIndex(int targetWaypointIndex) {
        this.targetWaypointIndex = targetWaypointIndex;
    }

    public boolean isActive() {
        return active;
    }
    public void setActive(boolean active) {
        this.active = active;
    }
}
