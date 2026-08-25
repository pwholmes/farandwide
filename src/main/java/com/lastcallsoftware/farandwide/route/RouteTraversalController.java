package com.lastcallsoftware.farandwide.route;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Advances every active assignment independently of how its entity is controlled. */
public final class RouteTraversalController {
    private static final double ARRIVAL_RADIUS = 5.0;

    private RouteTraversalController() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RouteTraversalController::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        RouteManager.synchronizeSelectedRouteWithNavigation();

        for (RouteAssignment assignment : RouteManager.getAssignments()) {
            if (!assignment.isActive()) {
                continue;
            }

            Entity assignee = minecraft.level.getEntity(assignment.getAssigneeEntityId());
            if (assignee != null) {
                advanceIfTargetReached(assignee, assignment);
            }
        }
    }

    private static void advanceIfTargetReached(Entity assignee, RouteAssignment assignment) {
        Route route = RouteManager.getRoute(assignment.getRouteId());
        if (route == null) {
            assignment.setActive(false);
            return;
        }

        Waypoint target = RouteManager.getTargetWaypoint(assignment);
        if (target == null) {
            return;
        }

        double deltaX = assignee.getX() - target.position().x;
        double deltaZ = assignee.getZ() - target.position().z;
        if (deltaX * deltaX + deltaZ * deltaZ > ARRIVAL_RADIUS * ARRIVAL_RADIUS) {
            return;
        }

        int waypointCount = route.getWaypoints().size();
        if (waypointCount == 1) {
            assignment.setActive(false);
            return;
        }

        TraversalType traversalType = assignment.getTraversalType(route);

        switch (traversalType) {
            case ONE_WAY -> {
                int nextIndex = assignment.getTargetWaypointIndex() + 1;
                if (nextIndex >= waypointCount) {
                    assignment.setActive(false);
                } else {
                    assignment.setTargetWaypointIndex(nextIndex);
                }
            }
            case LOOP -> {
                int nextIndex = assignment.getTargetWaypointIndex() + 1;
                assignment.setTargetWaypointIndex(nextIndex >= waypointCount ? 0 : nextIndex);
            }
            case REVERSE -> {
                int nextIndex = assignment.getTargetWaypointIndex() + assignment.getTraversalDirection();
                if (nextIndex >= waypointCount) {
                    assignment.setTraversalDirection(-1);
                    assignment.setTargetWaypointIndex(waypointCount - 2);
                } else if (nextIndex < 0) {
                    assignment.setTraversalDirection(1);
                    assignment.setTargetWaypointIndex(1);
                } else {
                    assignment.setTargetWaypointIndex(nextIndex);
                }
            }
        }
    }
}
