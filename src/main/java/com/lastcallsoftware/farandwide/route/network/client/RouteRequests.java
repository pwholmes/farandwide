package com.lastcallsoftware.farandwide.route.network.client;

import com.lastcallsoftware.farandwide.route.TraversalType;
import com.lastcallsoftware.farandwide.route.Waypoint;
import com.lastcallsoftware.farandwide.route.WaypointAction;
import com.lastcallsoftware.farandwide.route.network.payload.AssignmentMutationPayload;
import com.lastcallsoftware.farandwide.route.network.payload.RequestAssignmentSnapshotPayload;
import com.lastcallsoftware.farandwide.route.network.payload.RequestRouteSnapshotPayload;
import com.lastcallsoftware.farandwide.route.network.payload.RouteMutationPayload;
import com.lastcallsoftware.farandwide.route.network.payload.RouteActivationMutationPayload;
import com.lastcallsoftware.farandwide.route.network.payload.SelectRoutePayload;
import com.lastcallsoftware.farandwide.route.network.payload.WaypointMutationPayload;
import com.lastcallsoftware.farandwide.route.network.payload.VehicleActivationMutationPayload;
import com.lastcallsoftware.farandwide.route.network.payload.VehicleDirectionMutationPayload;
import com.lastcallsoftware.farandwide.route.network.payload.VehicleUnassignmentMutationPayload;
import com.lastcallsoftware.farandwide.route.network.payload.VehicleWaypointMutationPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
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

    public static void createWaypoint(int routeId, Vec3 position, Identifier dimension, WaypointAction action) {
        send(WaypointMutationPayload.create(routeId, position, dimension, action));
    }

    public static void createWaypoint(int routeId, Vec3 position, Identifier dimension, WaypointAction action,
            double arrivalRadius) {
        send(WaypointMutationPayload.create(routeId, position, dimension, action, arrivalRadius));
    }

    public static void replaceWaypoint(int routeId, Waypoint waypoint, int targetPosition) {
        send(WaypointMutationPayload.replace(routeId, waypoint, targetPosition));
    }

    public static void convertWaypoint(int routeId, int waypointId, WaypointAction action) {
        send(WaypointMutationPayload.convert(routeId, waypointId, action));
    }

    public static void deleteWaypoint(int routeId, int waypointId) {
        send(WaypointMutationPayload.delete(routeId, waypointId));
    }

    public static void assignRoute(int routeId) {
        send(new AssignmentMutationPayload(AssignmentMutationPayload.Action.ASSIGN, routeId));
    }

    public static void toggleAssignment() {
        send(new AssignmentMutationPayload(AssignmentMutationPayload.Action.TOGGLE_ACTIVE, 0));
    }

    public static void toggleVehicle() {
        send(new AssignmentMutationPayload(AssignmentMutationPayload.Action.TOGGLE_VEHICLE, 0));
    }

    public static void setRouteAssignmentsActive(int routeId, boolean active) {
        send(new RouteActivationMutationPayload(routeId, active));
    }

    public static void moveVehicleTargetWaypoint(int assigneeId, int delta) {
        send(new VehicleWaypointMutationPayload(assigneeId, delta));
    }

    public static void reverseVehicleDirection(int assigneeId) {
        send(new VehicleDirectionMutationPayload(assigneeId));
    }

    public static void reverseVehicleDirection() {
        send(new AssignmentMutationPayload(AssignmentMutationPayload.Action.REVERSE_VEHICLE, 0));
    }

    public static void setVehicleAssignmentActive(int assigneeId, boolean active) {
        send(new VehicleActivationMutationPayload(assigneeId, active));
    }

    public static void unassignVehicle(int assigneeId) {
        send(new VehicleUnassignmentMutationPayload(assigneeId));
    }

    private static RouteMutationPayload routeMutation(RouteMutationPayload.Action action, int routeId) {
        return new RouteMutationPayload(action, routeId, "", TraversalType.ONE_WAY);
    }

    private static void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }
}
