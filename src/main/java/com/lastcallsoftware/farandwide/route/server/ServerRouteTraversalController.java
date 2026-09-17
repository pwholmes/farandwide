package com.lastcallsoftware.farandwide.route.server;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.ObjIntConsumer;
import com.lastcallsoftware.farandwide.route.network.OrderNetwork;

import com.lastcallsoftware.farandwide.route.network.payload.AssignmentSnapshotPayload;
import com.lastcallsoftware.farandwide.route.network.RouteNetwork;
import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.route.RouteOperationResult;
import com.lastcallsoftware.farandwide.route.TraversalType;
import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.route.CargoBehavior;
import com.lastcallsoftware.farandwide.route.CargoFilter;
import com.lastcallsoftware.farandwide.route.Waypoint;
import com.lastcallsoftware.farandwide.route.WaypointAction;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideAttachments;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideSavedData;
import com.lastcallsoftware.farandwide.vehicle.server.ServerVehicleController;
import com.lastcallsoftware.farandwide.vehicle.server.VehicleChunkLoadingManager;
import com.lastcallsoftware.farandwide.vehicle.server.cargo.CargoVehicleInventory;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

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
    private static final Map<Integer, CargoTransferSession> cargoTransfersByAssignee = new HashMap<>();
    private static final Map<Integer, Long> reverseDwellUntilByAssignee = new HashMap<>();

    private ServerRouteTraversalController() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ServerRouteTraversalController::onServerTick);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        FarAndWideSavedData data = FarAndWideSavedData.get(event.getServer());
        Map<Integer, RouteAssignment> assignments = data.getAssignmentsByAssignee();
        if (assignments.isEmpty()) {
            reverseDwellUntilByAssignee.clear();
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

        reverseDwellUntilByAssignee.keySet().removeIf(assigneeId -> {
            RouteAssignment assignment = assignments.get(assigneeId);
            return assignment == null || !assignment.isActive();
        });
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
            cargoTransfersByAssignee.remove(assigneeId);
            VehicleChunkLoadingManager.release(entity);
            return;
        }
        Route route = data.getRoute(assignment.getRouteId());
        if (route == null) {
            if (data.setAssignmentActive(assigneeId, false)) {
                ServerVehicleController.stop(entity);
                VehicleChunkLoadingManager.release(entity);
                syncToControllingPlayer(server, entity, data.getAssignment(assigneeId));
            }
            return;
        }
        if (!VehicleChunkLoadingManager.update(entity, assigneeId)) {
            data.setAssignmentActive(assigneeId, false);
            ServerVehicleController.stop(entity);
            syncToControllingPlayer(server, entity, data.getAssignment(assigneeId));
            notifyControllingPlayer(server, entity, RouteOperationResult.CHUNK_LOADING_LIMIT);
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
        if (!target.hasArrived(entity)) {
            ServerVehicleController.navigate(entity, target);
            return;
        }

        ServerVehicleController.stop(entity);
        boolean departingFromRestartAnchor = isRestartAnchor(route, assignment);
        if (target.action() instanceof WaypointAction.Cargo cargo
                && !processCargo(assigneeId, route.getId(), entity, target, cargo.behavior(), departingFromRestartAnchor)) {
            return;
        }
        if (waitForReverseDeparture(assigneeId, route, assignment, entity.level().getGameTime())) {
            return;
        }
        if (advanceAssignment(data, assigneeId, route, assignment)) {
            RouteAssignment updated = data.getAssignment(assigneeId);
            if (updated == null || !updated.isActive()) {
                VehicleChunkLoadingManager.release(entity);
            }
            syncToControllingPlayer(server, entity, updated);
            RouteNetwork.broadcastVehicleAssignments(server);
        }
    }

    static boolean processArrival(FarAndWideSavedData data, int assigneeId, Route route,
            RouteAssignment assignment, Waypoint target, Consumer<CargoBehavior> cargoProcessor) {
        if (target.action() instanceof WaypointAction.Cargo cargo) {
            cargoProcessor.accept(cargo.behavior());
        }
        return advanceAssignment(data, assigneeId, route, assignment);
    }

    private static boolean waitForReverseDeparture(int assigneeId, Route route, RouteAssignment assignment,
            long gameTime) {
        if (!reversesAtTarget(route, assignment)) {
            reverseDwellUntilByAssignee.remove(assigneeId);
            return false;
        }
        Long dwellUntil = reverseDwellUntilByAssignee.get(assigneeId);
        if (dwellUntil == null) {
            reverseDwellUntilByAssignee.put(assigneeId,
                    gameTime + (long) Math.ceil(Constants.Cargo.DWELL_SECONDS * 20.0));
            return true;
        }
        if (gameTime < dwellUntil) {
            return true;
        }
        reverseDwellUntilByAssignee.remove(assigneeId);
        return false;
    }

    static boolean reversesAtTarget(Route route, RouteAssignment assignment) {
        TraversalType type = assignment.getTraversalType(route);
        if ((type != TraversalType.REVERSE && type != TraversalType.ROUND_TRIP)
                || route.getWaypoints().size() < 2) {
            return false;
        }
        int target = assignment.getTargetWaypointIndex();
        return (type == TraversalType.REVERSE && target == 0 && assignment.getTraversalDirection() < 0)
                || (target == route.getWaypoints().size() - 1 && assignment.getTraversalDirection() > 0);
    }

    private static boolean isRestartAnchor(Route route, RouteAssignment assignment) {
        if (!assignment.isRestartAnchor()
                || (assignment.getTraversalType(route) != TraversalType.ONE_WAY
                    && assignment.getTraversalType(route) != TraversalType.ROUND_TRIP)
                || route.getWaypoints().size() <= 1) {
            return false;
        }
        int target = assignment.getTargetWaypointIndex();
        return target == 0 || target == route.getWaypoints().size() - 1;
    }

    /** One-way and round-trip routes keep traversing while cargo is still owed to an order. */
    private static TraversalType effectiveTraversalType(FarAndWideSavedData data, Route route,
            RouteAssignment assignment) {
        return (route.getTraversalType() == TraversalType.ONE_WAY
                || route.getTraversalType() == TraversalType.ROUND_TRIP) && data.hasOutstandingOrdersOnRoute(route.getId())
                ? TraversalType.REVERSE : assignment.getTraversalType(route);
    }

    private static boolean processCargo(int assigneeId, int routeId, Entity entity, Waypoint waypoint,
            CargoBehavior behavior, boolean departing) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return true;
        }
        Optional<ResourceHandler<ItemResource>> vehicle = CargoVehicleInventory.find(entity);
        Optional<ResourceHandler<ItemResource>> loadStation = CargoStationResolver.find(level, waypoint, behavior.loadStation());
        Optional<ResourceHandler<ItemResource>> unloadStation = CargoStationResolver.find(level, waypoint, behavior.unloadStation());
        CargoTransferSession session = cargoTransfersByAssignee.get(assigneeId);
        if (session == null || !session.matches(routeId, waypoint.id(), behavior, departing)) {
            session = new CargoTransferSession(routeId, waypoint.id(), behavior, departing);
            cargoTransfersByAssignee.put(assigneeId, session);
        }
        IntSupplier unload = behavior.operation().unloads()
                ? () -> transferCargoStack(vehicle, unloadStation, behavior.unloadFilter(), level, entity, false,
                        (resource, amount) -> {
                            if (behavior.unloadStation().isPresent()) {
                                FarAndWideSavedData.OrderCreditResult credit = FarAndWideSavedData.get(level.getServer())
                                        .creditOrdersAndFindCompleted(routeId, waypoint.id(), behavior.unloadStation().get(),
                                                resource, amount);
                                if (credit.changed()) {
                                    for (FarAndWideSavedData.CompletedOrder completedOrder : credit.completedOrders()) {
                                        net.minecraft.server.level.ServerPlayer owner = level.getServer().getPlayerList()
                                                .getPlayer(completedOrder.ownerId());
                                        if (owner != null) owner.sendSystemMessage(
                                                Component.translatable("message.farandwide.order.delivered", completedOrder.number()), true);
                                    }
                                }
                                if (credit.changed()) {
                                OrderNetwork.broadcastOrders(level.getServer());
                                }
                            }
                        })
                : () -> 0;
        IntSupplier load = behavior.operation().loads()
                ? () -> transferCargoStack(loadStation, vehicle, behavior.loadFilter(), level, entity, true,
                        (resource, amount) -> {})
                : () -> 0;
        boolean finished = session.tick(level.getGameTime(), unload, load);
        if (finished) {
            cargoTransfersByAssignee.remove(assigneeId);
        }
        return finished;
    }

    private static int transferCargoStack(Optional<ResourceHandler<ItemResource>> source,
            Optional<ResourceHandler<ItemResource>> destination, CargoFilter filter,
            ServerLevel level, Entity entity, boolean loading, ObjIntConsumer<ItemResource> receipt) {
        int moved = source.flatMap(handler -> destination.map(station -> CargoTransferService.transferItems(
                handler, station, resource -> CargoTransferService.matches(filter, resource), receipt))).orElse(0);
        if (moved > 0) {
            playCargoTransferSound(level, entity, loading);
        }
        return moved;
    }

    private static void playCargoTransferSound(ServerLevel level, Entity entity, boolean loading) {
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ITEM_PICKUP,
                SoundSource.BLOCKS, 0.35F, loading ? 1.15F : 0.85F);
    }

    private enum CargoStage {
        UNLOAD,
        LOAD
    }

    static final class CargoTransferSession {
        private final int routeId;
        private final int waypointId;
        private final CargoBehavior behavior;
        private final boolean departing;
        private CargoStage stage;
        private long nextTransferTick;

        CargoTransferSession(int routeId, int waypointId, CargoBehavior behavior) {
            this(routeId, waypointId, behavior, false);
        }

        CargoTransferSession(int routeId, int waypointId, CargoBehavior behavior, boolean departing) {
            this.routeId = routeId;
            this.waypointId = waypointId;
            this.behavior = behavior;
            this.departing = departing;
            this.stage = departing || !behavior.operation().unloads() ? CargoStage.LOAD : CargoStage.UNLOAD;
        }

        /** Cargo dwells after unloading before loading or departure; unavailable stages are skipped. */
        boolean tick(long gameTime, IntSupplier unload, IntSupplier load) {
            if (gameTime < nextTransferTick) {
                return false;
            }
            if (stage == CargoStage.UNLOAD) {
                if (unload.getAsInt() > 0) {
                    nextTransferTick = gameTime + transferDelayTicks();
                    return false;
                }
                stage = CargoStage.LOAD;
                long dwellTicks = (long) Math.ceil(Constants.Cargo.DWELL_SECONDS * 20.0);
                nextTransferTick = gameTime + dwellTicks;
                if (dwellTicks > 0) {
                    return false;
                }
            }
            if (load.getAsInt() > 0) {
                nextTransferTick = gameTime + transferDelayTicks();
                return false;
            }
            return true;
        }

        boolean matches(int routeId, int waypointId, CargoBehavior behavior, boolean departing) {
            return this.routeId == routeId && this.waypointId == waypointId && this.behavior.equals(behavior)
                    && this.departing == departing;
        }

        private static long transferDelayTicks() {
            return (long) Math.ceil(Constants.Cargo.TRANSFER_DELAY_SECONDS * 20.0);
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
        return switch (effectiveTraversalType(data, route, assignment)) {
            case ONE_WAY -> {
                if (isRestartAnchor(route, assignment)) {
                    int direction = assignment.getTargetWaypointIndex() == 0 ? 1 : -1;
                    yield data.updateAssignmentProgress(
                            assigneeId, assignment.getTargetWaypointIndex() + direction, direction);
                }
                int next = assignment.getTargetWaypointIndex() + assignment.getTraversalDirection();
                if (next >= waypointCount) {
                    yield data.stopAssignmentAtWaypoint(
                            assigneeId, waypointCount - 1, assignment.getTraversalDirection());
                }
                if (next < 0) {
                    yield data.stopAssignmentAtWaypoint(
                            assigneeId, 0, assignment.getTraversalDirection());
                }
                yield data.updateAssignmentProgress(assigneeId, next, assignment.getTraversalDirection());
            }
            case LOOP -> data.updateAssignmentProgress(assigneeId,
                    (assignment.getTargetWaypointIndex() + 1) % waypointCount, assignment.getTraversalDirection());
            case ROUND_TRIP -> {
                int direction = isRestartAnchor(route, assignment) ? 1 : assignment.getTraversalDirection();
                int next = assignment.getTargetWaypointIndex() + direction;
                if (next >= waypointCount) {
                    yield data.updateAssignmentProgress(assigneeId, waypointCount - 2, -1);
                }
                if (next < 0) {
                    yield data.stopAssignmentAtWaypoint(assigneeId, 0, -1);
                }
                yield data.updateAssignmentProgress(assigneeId, next, direction);
            }
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

    private static void notifyControllingPlayer(net.minecraft.server.MinecraftServer server, Entity assignee,
            RouteOperationResult result) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == assignee || player.getVehicle() == assignee) {
                player.sendSystemMessage(Component.translatable(result.translationKey()));
            }
        }
    }
}
