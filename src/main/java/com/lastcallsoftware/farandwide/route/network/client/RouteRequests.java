package com.lastcallsoftware.farandwide.route.network.client;

import com.lastcallsoftware.farandwide.route.TraversalType;
import com.lastcallsoftware.farandwide.route.network.payload.AssignmentMutationPayload;
import com.lastcallsoftware.farandwide.route.network.payload.RequestAssignmentSnapshotPayload;
import com.lastcallsoftware.farandwide.route.network.payload.RequestRouteSnapshotPayload;
import com.lastcallsoftware.farandwide.route.network.payload.RouteMutationPayload;
import com.lastcallsoftware.farandwide.route.network.payload.SelectRoutePayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Client-only transport boundary for route requests.
 *
 * <p>{@code RouteManager} exposes player-facing operations and owns cache policy;
 * this class knows only how those operations map to packet records. Screens and
 * commands must call {@code RouteManager}, not this class. Keeping packet creation
 * here prevents a payload or NeoForge networking change from spreading through UI
 * code.
 *
 * <p>Several actions share {@code RouteMutationPayload}; fields unused by a given
 * action receive harmless defaults in {@link #routeMutation}. The authoritative
 * handler reads only the fields required by that action.
 */
public final class RouteRequests {
    private RouteRequests() {
    }

    public static void requestRoutes() {
        send(new RequestRouteSnapshotPayload());
    }

    public static void requestAssignment() {
        send(new RequestAssignmentSnapshotPayload());
    }

    public static void createRoute(String name, TraversalType traversalType) {
        send(RouteMutationPayload.create(name, traversalType));
    }

    public static void updateRoute(int routeId, String name, TraversalType traversalType) {
        send(new RouteMutationPayload(RouteMutationPayload.Action.UPDATE, routeId, name, traversalType));
    }

    public static void deleteRoute(int routeId) {
        send(routeMutation(RouteMutationPayload.Action.DELETE, routeId));
    }

    public static void selectRoute(int routeId) {
        send(new SelectRoutePayload(routeId));
    }

    public static void addWaypoint(int routeId) {
        send(routeMutation(RouteMutationPayload.Action.ADD_WAYPOINT, routeId));
    }

    public static void removeWaypoint(int routeId) {
        send(routeMutation(RouteMutationPayload.Action.REMOVE_WAYPOINT, routeId));
    }

    public static void toggleWaypoint(int routeId) {
        send(routeMutation(RouteMutationPayload.Action.TOGGLE_WAYPOINT, routeId));
    }

    public static void assignRoute(int routeId) {
        send(new AssignmentMutationPayload(AssignmentMutationPayload.Action.ASSIGN, routeId));
    }

    public static void toggleAssignment() {
        send(new AssignmentMutationPayload(AssignmentMutationPayload.Action.TOGGLE_ACTIVE, 0));
    }

    private static RouteMutationPayload routeMutation(RouteMutationPayload.Action action, int routeId) {
        return new RouteMutationPayload(action, routeId, "", TraversalType.ONE_WAY);
    }

    private static void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }
}
