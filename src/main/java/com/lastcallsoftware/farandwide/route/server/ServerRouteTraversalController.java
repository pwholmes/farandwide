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
import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.route.CargoBehavior;
import com.lastcallsoftware.farandwide.route.CargoFilter;
import com.lastcallsoftware.farandwide.route.CargoOperation;
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
        if (target.action() instanceof WaypointAction.Cargo cargo
                && !processCargo(assigneeId, route.getId(), entity, target, cargo.behavior())) {
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

    private static boolean isOneWayRestartAnchor(Route route, RouteAssignment assignment) {
        if (!assignment.isRestartAnchor()
                || assignment.getTraversalType(route) != com.lastcallsoftware.farandwide.route.TraversalType.ONE_WAY
                || route.getWaypoints().size() <= 1) {
            return false;
        }
        int target = assignment.getTargetWaypointIndex();
        return target == 0 || target == route.getWaypoints().size() - 1;
    }

    private static boolean processCargo(int assigneeId, int routeId, Entity entity, Waypoint waypoint,
            CargoBehavior behavior) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return true;
        }
        Optional<ResourceHandler<ItemResource>> vehicle = CargoVehicleInventory.find(entity);
        Optional<ResourceHandler<ItemResource>> loadStation = CargoStationResolver.find(level, waypoint, behavior.loadStation());
        Optional<ResourceHandler<ItemResource>> unloadStation = CargoStationResolver.find(level, waypoint, behavior.unloadStation());
        CargoTransferSession session = cargoTransfersByAssignee.get(assigneeId);
        if (session == null || !session.matches(routeId, waypoint.id(), behavior)) {
            session = new CargoTransferSession(routeId, waypoint.id(), behavior);
            cargoTransfersByAssignee.put(assigneeId, session);
        }
        boolean finished = session.tick(level.getGameTime(),
                () -> transferCargoStack(vehicle, unloadStation, behavior.unloadFilter(), level, entity, false,
                        (resource, amount) -> {
                            if (behavior.unloadStation().isPresent()
                                    && FarAndWideSavedData.get(level.getServer()).creditOrders(routeId, waypoint.id(),
                                            behavior.unloadStation().get(), resource, amount)) {
                                OrderNetwork.broadcastOrders(level.getServer());
                            }
                        }),
                () -> transferCargoStack(loadStation, vehicle, behavior.loadFilter(), level, entity, true,
                        (resource, amount) -> {}));
        if (finished) {
            cargoTransfersByAssignee.remove(assigneeId);
        }
        return finished;
    }

    private static int transferCargoStack(Optional<ResourceHandler<ItemResource>> source,
            Optional<ResourceHandler<ItemResource>> destination, CargoFilter filter,
            ServerLevel level, Entity entity, boolean loading, ObjIntConsumer<ItemResource> receipt) {
        int moved = source.flatMap(handler -> destination.map(station -> CargoTransferService.transferOneStack(
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
        private CargoStage stage;
        private long nextTransferTick;

        CargoTransferSession(int routeId, int waypointId, CargoBehavior behavior) {
            this.routeId = routeId;
            this.waypointId = waypointId;
            this.behavior = behavior;
            this.stage = behavior.operation() == CargoOperation.LOAD ? CargoStage.LOAD : CargoStage.UNLOAD;
        }

        /** Empty stages finish immediately; every moved stack delays further transfers and departure. */
        boolean tick(long gameTime, IntSupplier unload, IntSupplier load) {
            if (gameTime < nextTransferTick) {
                return false;
            }
            if (stage == CargoStage.UNLOAD) {
                if (unload.getAsInt() > 0) {
                    nextTransferTick = gameTime + Constants.Cargo.TRANSFER_INTERVAL_TICKS;
                    return false;
                }
                if (behavior.operation() == CargoOperation.UNLOAD) {
                    return true;
                }
                stage = CargoStage.LOAD;
            }
            if (load.getAsInt() > 0) {
                nextTransferTick = gameTime + Constants.Cargo.TRANSFER_INTERVAL_TICKS;
                return false;
            }
            return true;
        }

        boolean matches(int routeId, int waypointId, CargoBehavior behavior) {
            return this.routeId == routeId && this.waypointId == waypointId && this.behavior.equals(behavior);
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
                if (isOneWayRestartAnchor(route, assignment)) {
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
