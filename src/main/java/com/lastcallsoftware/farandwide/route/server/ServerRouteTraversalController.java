package com.lastcallsoftware.farandwide.route.server;

import java.util.HashMap;
import java.util.Map;

import com.lastcallsoftware.farandwide.route.network.payload.AssignmentSnapshotPayload;
import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.route.Waypoint;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideAttachments;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideSavedData;
import com.lastcallsoftware.farandwide.vehicle.server.ServerVehicleController;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Advances active assignments on the authoritative server tick.
 *
 * <p>The controller first maps stable assignee IDs to currently loaded entities.
 * Unloaded entities keep their persisted progress but are not advanced. Arrival
 * requires the entity and target waypoint to share a dimension and compares
 * three-dimensional distance.
 *
 * <p>Progress changes go through {@code FarAndWideSavedData}; after replacement,
 * the controller fetches the new immutable assignment and sends it to any player
 * controlling that entity.
 */
public final class ServerRouteTraversalController {
    private static final double ARRIVAL_RADIUS = 3.0;

    private ServerRouteTraversalController() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ServerRouteTraversalController::onServerTick);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        FarAndWideSavedData data = FarAndWideSavedData.get(event.getServer());
        Map<Integer, RouteAssignment> assignments = data.getAssignmentsByAssignee();
        if (assignments.isEmpty()) {
            return;
        }

        Map<Integer, Entity> entitiesByAssigneeId = new HashMap<>();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!entity.hasData(FarAndWideAttachments.ASSIGNEE_ID.get())) {
                    continue;
                }
                int assigneeId = entity.getData(FarAndWideAttachments.ASSIGNEE_ID.get());
                if (assignments.containsKey(assigneeId)) {
                    entitiesByAssigneeId.put(assigneeId, entity);
                }
            }
        }

        assignments.forEach((assigneeId, assignment) -> {
            Entity entity = entitiesByAssigneeId.get(assigneeId);
            if (entity != null) {
                tickAssignment(event.getServer(), data, entity, assigneeId, assignment);
            }
        });
    }

    private static void tickAssignment(net.minecraft.server.MinecraftServer server, FarAndWideSavedData data,
            Entity entity, int assigneeId,
            RouteAssignment assignment) {
        if (!assignment.isActive()) {
            ServerVehicleController.stop(entity);
            return;
        }
        Route route = data.getRoute(assignment.getRouteId());
        if (route == null) {
            if (data.setAssignmentActive(assigneeId, false)) {
                ServerVehicleController.stop(entity);
                syncToControllingPlayer(server, entity, data.getAssignment(assigneeId));
            }
            return;
        }
        Waypoint target = assignment.getTargetWaypointIndex() >= 0
                && assignment.getTargetWaypointIndex() < route.getWaypoints().size()
                ? route.getWaypoints().get(assignment.getTargetWaypointIndex())
                : null;
        if (target == null || !target.dimension().equals(entity.level().dimension().identifier())) {
            ServerVehicleController.stop(entity);
            return;
        }
        double deltaX = entity.getX() - target.position().x;
        double deltaY = entity.getY() - target.position().y;
        double deltaZ = entity.getZ() - target.position().z;
        if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ > ARRIVAL_RADIUS * ARRIVAL_RADIUS) {
            ServerVehicleController.navigate(entity, target);
            return;
        }

        if (advanceAssignment(data, assigneeId, route, assignment)) {
            syncToControllingPlayer(server, entity, data.getAssignment(assigneeId));
        }
    }

    static boolean advanceAssignment(FarAndWideSavedData data, int assigneeId, Route route,
            RouteAssignment assignment) {
        /*
         * This method contains only the traversal state transition, separate from
         * entity scanning and arrival detection so all traversal modes can be
         * characterized by ordinary unit tests.
         */
        int waypointCount = route.getWaypoints().size();
        if (waypointCount <= 1) {
            return data.setAssignmentActive(assigneeId, false);
        }
        return switch (assignment.getTraversalType(route)) {
            case ONE_WAY -> {
                int next = assignment.getTargetWaypointIndex() + 1;
                if (next >= waypointCount) {
                    yield data.setAssignmentActive(assigneeId, false);
                }
                yield data.updateAssignmentProgress(assigneeId, next, assignment.getTraversalDirection());
            }
            case LOOP -> data.updateAssignmentProgress(assigneeId,
                    (assignment.getTargetWaypointIndex() + 1) % waypointCount, assignment.getTraversalDirection());
            case REVERSE -> {
                int next = assignment.getTargetWaypointIndex() + assignment.getTraversalDirection();
                if (next >= waypointCount) {
                    yield data.updateAssignmentProgress(assigneeId, waypointCount - 2, -1);
                }
                if (next < 0) {
                    yield data.updateAssignmentProgress(assigneeId, 1, 1);
                }
                yield data.updateAssignmentProgress(assigneeId, next, assignment.getTraversalDirection());
            }
        };
    }

    private static void syncToControllingPlayer(net.minecraft.server.MinecraftServer server, Entity assignee,
            RouteAssignment assignment) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == assignee || player.getVehicle() == assignee) {
                PacketDistributor.sendToPlayer(player, new AssignmentSnapshotPayload(assignee.getId(), assignment));
            }
        }
    }
}
