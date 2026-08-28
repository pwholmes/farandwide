package com.lastcallsoftware.farandwide.route.network;

import com.lastcallsoftware.farandwide.route.network.payload.AssignmentMutationPayload;
import com.lastcallsoftware.farandwide.route.network.payload.AssignmentSnapshotPayload;
import com.lastcallsoftware.farandwide.route.network.payload.RequestAssignmentSnapshotPayload;
import com.lastcallsoftware.farandwide.route.network.payload.RequestRouteSnapshotPayload;
import com.lastcallsoftware.farandwide.route.network.payload.RouteMutationPayload;
import com.lastcallsoftware.farandwide.route.network.payload.RouteOperationResultPayload;
import com.lastcallsoftware.farandwide.route.network.payload.RouteSnapshotPayload;
import com.lastcallsoftware.farandwide.route.network.payload.SelectRoutePayload;
import com.lastcallsoftware.farandwide.route.network.payload.WaypointMutationPayload;
import com.lastcallsoftware.farandwide.route.server.RouteService;
import com.lastcallsoftware.farandwide.route.RouteOperationResult;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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
        var registrar = event.registrar("4");
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
                    }
                    replyWithRoutes(player, context);
                });
        registrar.playToServer(RequestAssignmentSnapshotPayload.TYPE, RequestAssignmentSnapshotPayload.STREAM_CODEC,
                (payload, context) -> sendAssignmentSnapshot((ServerPlayer) context.player()));
        registrar.playToServer(AssignmentMutationPayload.TYPE, AssignmentMutationPayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    RouteOperationResult result = payload.action() == AssignmentMutationPayload.Action.ASSIGN
                            ? RouteService.assignRoute(player, payload.routeId())
                            : RouteService.toggleAssignment(player);
                    replyWithResult(context, result);
                    if (payload.action() == AssignmentMutationPayload.Action.TOGGLE_ACTIVE
                            && result == RouteOperationResult.SUCCESS) {
                        sendAssignmentSnapshotsForRoute(player, RouteService.getSelectedRouteId(player));
                    } else {
                        sendAssignmentSnapshot(player);
                    }
                });
        registrar.playToClient(RouteSnapshotPayload.TYPE, RouteSnapshotPayload.STREAM_CODEC);
        registrar.playToClient(AssignmentSnapshotPayload.TYPE, AssignmentSnapshotPayload.STREAM_CODEC);
        registrar.playToClient(RouteOperationResultPayload.TYPE, RouteOperationResultPayload.STREAM_CODEC);
    }

    private static void sendAssignmentSnapshot(ServerPlayer player) {
        // Assignment snapshots are sent only to the controlling player. The
        // service has already translated the persistent assignee key to the
        // entity's current runtime ID.
        RouteService.AssignmentState state = RouteService.getAssignment(player);
        PacketDistributor.sendToPlayer(player, new AssignmentSnapshotPayload(state.entityId(), state.assignment()));
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

    private static void replyWithRoutes(ServerPlayer player,
            net.neoforged.neoforge.network.handling.IPayloadContext context) {
        RouteService.RouteState state = RouteService.getRoutes(player);
        // Unlike broadcasts, direct replies include this player's selection.
        context.reply(RouteSnapshotPayload.from(state.routes(), state.selectedRouteId()));
    }

    private static void replyWithResult(net.neoforged.neoforge.network.handling.IPayloadContext context,
            RouteOperationResult result) {
        context.reply(new RouteOperationResultPayload(result));
    }
}
