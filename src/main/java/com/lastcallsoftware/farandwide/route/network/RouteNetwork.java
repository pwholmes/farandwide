package com.lastcallsoftware.farandwide.route.network;

import com.lastcallsoftware.farandwide.route.network.payload.AssignmentMutationPayload;
import com.lastcallsoftware.farandwide.route.network.payload.AssignmentSnapshotPayload;
import com.lastcallsoftware.farandwide.route.network.payload.RequestAssignmentSnapshotPayload;
import com.lastcallsoftware.farandwide.route.network.payload.RequestRouteSnapshotPayload;
import com.lastcallsoftware.farandwide.route.network.payload.RouteMutationPayload;
import com.lastcallsoftware.farandwide.route.network.payload.RouteActivationMutationPayload;
import com.lastcallsoftware.farandwide.route.network.payload.RouteOperationResultPayload;
import com.lastcallsoftware.farandwide.route.network.payload.RouteSnapshotPayload;
import com.lastcallsoftware.farandwide.route.network.payload.SelectRoutePayload;
import com.lastcallsoftware.farandwide.route.network.payload.WaypointMutationPayload;
import com.lastcallsoftware.farandwide.route.network.payload.VehicleActivationMutationPayload;
import com.lastcallsoftware.farandwide.route.network.payload.VehicleAssignmentsSnapshotPayload;
import com.lastcallsoftware.farandwide.route.network.payload.VehicleWaypointMutationPayload;
import com.lastcallsoftware.farandwide.route.network.payload.VehicleUnassignmentMutationPayload;
import com.lastcallsoftware.farandwide.route.server.RouteService;
import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.route.RouteOperationResult;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import java.util.UUID;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Registers route payloads and adapts them to {@code RouteService} calls.
 *
 * <p>Handlers in this class must remain transport adapters: decode the payload,
 * invoke one authoritative service operation, return its result, and synchronize
 * the affected snapshot. Validation and permanent mutations do not belong here.
 * Payload records remain common because both physical sides must encode or decode
 * them; client-only handling is registered by {@code FarAndWideClient}.
 */
public final class RouteNetwork {
    private RouteNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("11");
        registrar.playToServer(RequestRouteSnapshotPayload.TYPE, RequestRouteSnapshotPayload.STREAM_CODEC,
                (payload, context) -> replyWithRoutes((ServerPlayer) context.player(), context));
        registrar.playToServer(SelectRoutePayload.TYPE, SelectRoutePayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    replyWithResult(context, RouteService.selectRoute(player, payload.routeId()));
                    replyWithRoutes(player, context);
                });
        registrar.playToServer(RouteMutationPayload.TYPE, RouteMutationPayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    RouteOperationResult result = switch (payload.action()) {
                        case CREATE -> RouteService.createRoute(player, payload.name(), payload.traversalType());
                        case UPDATE -> RouteService.updateRoute(
                                player, payload.routeId(), payload.name(), payload.traversalType());
                        case DELETE -> RouteService.deleteRoute(player, payload.routeId());
                        case ADD_WAYPOINT -> RouteService.addWaypoint(player, payload.routeId());
                    };
                    replyWithResult(context, result);
                    if (result == RouteOperationResult.SUCCESS) {
                        // Route definitions are shared server state, so every
                        // connected client replaces its cache after a mutation.
                        broadcastRoutes(player);
                        broadcastVehicleAssignments(player);
                    }
                    replyWithRoutes(player, context);
                });
        registrar.playToServer(WaypointMutationPayload.TYPE, WaypointMutationPayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    RouteOperationResult result = switch (payload.mutation()) {
                        case CREATE -> RouteService.createWaypoint(player, payload.routeId(), payload.position(),
                                payload.dimension(), payload.waypointAction(), payload.arrivalRadius());
                        case REPLACE -> RouteService.replaceWaypoint(player, payload.routeId(), payload.waypointId(),
                                payload.position(), payload.dimension(), payload.waypointAction(), payload.targetPosition(),
                                payload.arrivalRadius());
                        case CONVERT -> RouteService.convertWaypoint(
                                player, payload.routeId(), payload.waypointId(), payload.waypointAction());
                        case DELETE -> RouteService.deleteWaypoint(player, payload.routeId(), payload.waypointId());
                    };
                    replyWithResult(context, result);
                    if (result == RouteOperationResult.SUCCESS) {
                        broadcastRoutes(player);
                        broadcastVehicleAssignments(player);
                    }
                    replyWithRoutes(player, context);
                });
        registrar.playToServer(RequestAssignmentSnapshotPayload.TYPE, RequestAssignmentSnapshotPayload.STREAM_CODEC,
                (payload, context) -> sendAssignmentSnapshot((ServerPlayer) context.player()));
        registrar.playToServer(AssignmentMutationPayload.TYPE, AssignmentMutationPayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    RouteOperationResult result = switch (payload.action()) {
                        case ASSIGN -> RouteService.assignRoute(player, payload.routeId());
                        case TOGGLE_ACTIVE -> RouteService.toggleAssignment(player);
                        case TOGGLE_VEHICLE -> RouteService.toggleCurrentVehicle(player);
                    };
                    replyWithResult(context, result);
                    if (payload.action() == AssignmentMutationPayload.Action.TOGGLE_ACTIVE) {
                        sendAssignmentSnapshotsForRoute(player, RouteService.getSelectedRouteId(player));
                        broadcastVehicleAssignments(player);
                    } else {
                        sendAssignmentSnapshot(player);
                        if (result == RouteOperationResult.SUCCESS) {
                            broadcastVehicleAssignments(player);
                        }
                    }
                });
        registrar.playToServer(RouteActivationMutationPayload.TYPE, RouteActivationMutationPayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    RouteOperationResult result = RouteService.setRouteAssignmentsActive(
                            player, payload.routeId(), payload.active());
                    replyWithResult(context, result);
                    sendAssignmentSnapshotsForRoute(player, payload.routeId());
                    broadcastVehicleAssignments(player);
                });
        registrar.playToServer(VehicleWaypointMutationPayload.TYPE, VehicleWaypointMutationPayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    RouteOperationResult result = RouteService.moveVehicleTargetWaypoint(
                            player, payload.assigneeId(), payload.delta());
                    replyWithResult(context, result);
                    if (result == RouteOperationResult.SUCCESS) {
                        RouteService.AssignmentState state =
                                RouteService.getLoadedManagedAssignment(player, payload.assigneeId());
                        if (state != null) {
                            sendManagedAssignmentSnapshot(player, state);
                        }
                        broadcastVehicleAssignments(player);
                    }
                });
        registrar.playToServer(VehicleActivationMutationPayload.TYPE, VehicleActivationMutationPayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    RouteOperationResult result = RouteService.setVehicleAssignmentActive(
                            player, payload.assigneeId(), payload.active());
                    replyWithResult(context, result);
                    if (result == RouteOperationResult.SUCCESS) {
                        RouteService.AssignmentState state =
                                RouteService.getLoadedManagedAssignment(player, payload.assigneeId());
                        if (state != null) {
                            sendManagedAssignmentSnapshot(player, state);
                        }
                        broadcastVehicleAssignments(player);
                    }
                });
        registrar.playToServer(VehicleUnassignmentMutationPayload.TYPE,
                VehicleUnassignmentMutationPayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    RouteService.AssignmentState previousState =
                            RouteService.getLoadedManagedAssignment(player, payload.assigneeId());
                    RouteOperationResult result = RouteService.unassignVehicle(player, payload.assigneeId());
                    replyWithResult(context, result);
                    if (result == RouteOperationResult.SUCCESS) {
                        if (previousState != null) {
                            AssignmentSnapshotPayload snapshot =
                                    new AssignmentSnapshotPayload(previousState.entityId(), null);
                            if (previousState.entityId() == player.getId()) {
                                PacketDistributor.sendToPlayer(player, snapshot);
                            } else {
                                PacketDistributor.sendToAllPlayers(snapshot);
                            }
                        }
                        broadcastVehicleAssignments(player);
                    }
                });
        registrar.playToClient(RouteSnapshotPayload.TYPE, RouteSnapshotPayload.STREAM_CODEC);
        registrar.playToClient(AssignmentSnapshotPayload.TYPE, AssignmentSnapshotPayload.STREAM_CODEC);
        registrar.playToClient(VehicleAssignmentsSnapshotPayload.TYPE,
                VehicleAssignmentsSnapshotPayload.STREAM_CODEC);
        registrar.playToClient(RouteOperationResultPayload.TYPE, RouteOperationResultPayload.STREAM_CODEC);
    }

    private static void sendAssignmentSnapshot(ServerPlayer player) {
        // Assignment snapshots are sent only to the controlling player. The
        // service has already translated the persistent assignee key to the
        // entity's current runtime ID.
        RouteService.AssignmentState state = RouteService.getAssignment(player);
        PacketDistributor.sendToPlayer(player, new AssignmentSnapshotPayload(state.entityId(), state.assignment()));
    }

    private static void sendManagedAssignmentSnapshot(
            ServerPlayer requestingPlayer, RouteService.AssignmentState state) {
        AssignmentSnapshotPayload snapshot =
                new AssignmentSnapshotPayload(state.entityId(), state.assignment());
        if (state.entityId() == requestingPlayer.getId()) {
            PacketDistributor.sendToPlayer(requestingPlayer, snapshot);
        } else {
            PacketDistributor.sendToAllPlayers(snapshot);
        }
    }

    /** Synchronizes both possible HUD subjects after a mount ownership transition. */
    public static void syncMountTransition(ServerPlayer player, Entity vehicle) {
        RouteService.RouteState routes = RouteService.getRoutes(player);
        PacketDistributor.sendToPlayer(player, RouteSnapshotPayload.from(routes.routes(), routes.selectedRouteId()));
        RouteService.AssignmentState playerState = RouteService.getAssignment(player, player);
        RouteService.AssignmentState vehicleState = RouteService.getAssignment(player, vehicle);
        PacketDistributor.sendToPlayer(player,
                new AssignmentSnapshotPayload(playerState.entityId(), playerState.assignment()));
        PacketDistributor.sendToPlayer(player,
                new AssignmentSnapshotPayload(vehicleState.entityId(), vehicleState.assignment()));
        PacketDistributor.sendToPlayer(player,
                new VehicleAssignmentsSnapshotPayload(RouteService.getRouteManagementAssignments(player)));
    }

    /** Removes a destroyed vehicle from every client's assignment caches. */
    public static void broadcastVehicleRemoval(MinecraftServer server, int runtimeEntityId) {
        PacketDistributor.sendToAllPlayers(new AssignmentSnapshotPayload(runtimeEntityId, null));
        broadcastVehicleAssignments(server);
    }

    /** Reports an asynchronous activation failure and refreshes every management view. */
    public static void reportVehicleActivationFailure(
            MinecraftServer server, UUID requestingPlayer, RouteOperationResult result) {
        ServerPlayer player = server.getPlayerList().getPlayer(requestingPlayer);
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new RouteOperationResultPayload(result));
        }
        broadcastVehicleAssignments(server);
    }

    /** Publishes the runtime assignment after a last-known chunk reload finds its vehicle. */
    public static void broadcastLoadedVehicleAssignment(
            MinecraftServer server, Entity entity, RouteAssignment assignment) {
        PacketDistributor.sendToAllPlayers(new AssignmentSnapshotPayload(entity.getId(), assignment));
        broadcastVehicleAssignments(server);
    }

    private static void sendAssignmentSnapshotsForRoute(ServerPlayer source, int routeId) {
        for (RouteService.AssignmentState state : RouteService.getLoadedAssignmentsForRoute(source, routeId)) {
            PacketDistributor.sendToAllPlayers(new AssignmentSnapshotPayload(state.entityId(), state.assignment()));
        }
    }

    private static void broadcastRoutes(ServerPlayer player) {
        RouteService.RouteState state = RouteService.getRoutesForBroadcast(player);
        PacketDistributor.sendToAllPlayers(RouteSnapshotPayload.from(state.routes(), state.selectedRouteId()));
    }

    private static void broadcastVehicleAssignments(ServerPlayer player) {
        broadcastVehicleAssignments(player.level().getServer());
    }

    public static void broadcastVehicleAssignments(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, new VehicleAssignmentsSnapshotPayload(
                    RouteService.getRouteManagementAssignments(player)));
        }
    }

    private static void replyWithRoutes(ServerPlayer player,
            net.neoforged.neoforge.network.handling.IPayloadContext context) {
        RouteService.RouteState state = RouteService.getRoutes(player);
        // Unlike broadcasts, direct replies include this player's selection.
        context.reply(RouteSnapshotPayload.from(state.routes(), state.selectedRouteId()));
        context.reply(new VehicleAssignmentsSnapshotPayload(RouteService.getRouteManagementAssignments(player)));
    }

    private static void replyWithResult(net.neoforged.neoforge.network.handling.IPayloadContext context,
            RouteOperationResult result) {
        context.reply(new RouteOperationResultPayload(result));
    }
}
