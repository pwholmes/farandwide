package com.lastcallsoftware.farandwide.route;

public class RouteAssignment {
    private final int routeId;
    private final int assigneeEntityId;
    private int targetWaypointIndex;
    private int traversalDirection = 1;
    private TraversalType traversalTypeOverride;
    private boolean active;

    public RouteAssignment(int routeId, int assigneeEntityId, int targetWaypointIndex) {
        this.routeId = routeId;
        this.assigneeEntityId = assigneeEntityId;
        this.targetWaypointIndex = targetWaypointIndex;
        this.active = true;
    }

    public int getRouteId() {
        return routeId;
    }

    public int getAssigneeEntityId() {
        return assigneeEntityId;
    }

    public int getTargetWaypointIndex() {
        return targetWaypointIndex;
    }

    public void setTargetWaypointIndex(int targetWaypointIndex) {
        this.targetWaypointIndex = targetWaypointIndex;
    }

    public int getTraversalDirection() {
        return traversalDirection;
    }

    public void setTraversalDirection(int traversalDirection) {
        this.traversalDirection = traversalDirection;
    }

    public TraversalType getTraversalType(Route route) {
        return traversalTypeOverride == null ? route.getTraversalType() : traversalTypeOverride;
    }

    public void setTraversalTypeOverride(TraversalType traversalTypeOverride) {
        this.traversalTypeOverride = traversalTypeOverride;
    }

    public void clearTraversalTypeOverride() {
        this.traversalTypeOverride = null;
    }

    public boolean isActive() {
        return active;
    }
    public void setActive(boolean active) {
        this.active = active;
    }
}
