package com.lastcallsoftware.farandwide.vehicle.server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.Config;
import com.lastcallsoftware.farandwide.FarAndWide;
import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.route.RouteOperationResult;
import com.lastcallsoftware.farandwide.route.network.RouteNetwork;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideAttachments;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideSavedData;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.eclipse.jdt.annotation.NonNull;

/** Maintains a small moving forced-chunk window around active route vehicles. */
public final class VehicleChunkLoadingManager {
    private static final TicketController TICKETS = new TicketController(
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "route_vehicles"),
            VehicleChunkLoadingManager::validateTickets);
    private static final Map<ServerLevel, Map<UUID, Set<ChunkPos>>> WINDOWS_BY_LEVEL = new WeakHashMap<>();
    private static final Map<UUID, PendingActivation> PENDING_ACTIVATIONS = new HashMap<>();
    private static final int ACTIVATION_TIMEOUT_TICKS = 200;

    private VehicleChunkLoadingManager() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(VehicleChunkLoadingManager::registerTicketController);
        NeoForge.EVENT_BUS.addListener(VehicleChunkLoadingManager::onServerStopped);
        NeoForge.EVENT_BUS.addListener(VehicleChunkLoadingManager::onEntityLeaveLevel);
        NeoForge.EVENT_BUS.addListener(VehicleChunkLoadingManager::onServerTick);
    }

    private static void registerTicketController(RegisterTicketControllersEvent event) {
        event.register(TICKETS);
    }

    /** Acquires the new window before releasing its old edge. */
    public static boolean update(Entity vehicle, int assigneeId) {
        if (!(vehicle.level() instanceof ServerLevel level) || !ServerVehicleController.supports(vehicle)) {
            release(vehicle);
            return true;
        }

        UUID owner = vehicle.getUUID();
        net.minecraft.resources.Identifier entityType =
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType());
        FarAndWideSavedData data = FarAndWideSavedData.get(level.getServer());
        data.registerVehicle(owner, assigneeId, entityType.getPath());
        data.updateVehicleCustomName(
                owner, vehicle.getCustomName() == null ? null : vehicle.getCustomName().getString());
        rememberLocation(vehicle, false);
        PENDING_ACTIVATIONS.remove(owner);
        boolean alreadyTracked = isTracked(owner);
        if (!canTrack(alreadyTracked, trackedVehicleCount(), Config.MAX_CHUNK_LOADED_VEHICLES.get())) {
            FarAndWide.LOGGER.warn("Pausing route vehicle {} (assignee {}) because the chunk-loading limit is {}",
                    owner, assigneeId, Config.MAX_CHUNK_LOADED_VEHICLES.get());
            return false;
        }
        releaseOtherLevels(owner, level);
        Set<ChunkPos> desired = windowAround(vehicle.chunkPosition(), Config.VEHICLE_CHUNK_RADIUS.get());
        installWindow(level, owner, desired);
        return true;
    }

    /** Releases all chunks currently tracked for a loaded entity. */
    public static void release(Entity vehicle) {
        if (!(vehicle.level() instanceof ServerLevel level)) {
            return;
        }
        rememberLocation(vehicle, true);
        PENDING_ACTIVATIONS.remove(vehicle.getUUID());
        Map<UUID, Set<ChunkPos>> levelWindows = WINDOWS_BY_LEVEL.get(level);
        if (levelWindows == null) {
            return;
        }
        UUID owner = vehicle.getUUID();
        Set<ChunkPos> previous = levelWindows.remove(owner);
        if (previous == null) {
            return;
        }
        for (ChunkPos chunk : previous) {
            TICKETS.forceChunk(level, owner, chunk.x(), chunk.z(), false, false);
        }
        if (levelWindows.isEmpty()) {
            WINDOWS_BY_LEVEL.remove(level);
        }
    }

    /** Force-loads the last known window so an unloaded vehicle can resume. */
    public static RouteOperationResult activateStoredVehicle(MinecraftServer server, UUID owner, int assigneeId,
            FarAndWideSavedData.VehicleLocation location, UUID requestingPlayer) {
        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, location.dimension());
        ServerLevel level = server.getLevel(dimensionKey);
        if (level == null) {
            return RouteOperationResult.VEHICLE_LOCATION_UNAVAILABLE;
        }
        boolean alreadyTracked = isTracked(owner);
        if (!canTrack(alreadyTracked, trackedVehicleCount(), Config.MAX_CHUNK_LOADED_VEHICLES.get())) {
            return RouteOperationResult.CHUNK_LOADING_LIMIT;
        }

        releaseOtherLevels(owner, level);
        installWindow(level, owner,
                windowAround(chunkAt(location.position()), Config.VEHICLE_CHUNK_RADIUS.get()));
        PENDING_ACTIVATIONS.put(owner,
                new PendingActivation(level, assigneeId, requestingPlayer, ACTIVATION_TIMEOUT_TICKS));
        return RouteOperationResult.SUCCESS;
    }

    /** Releases a pending or loaded window when an unloaded vehicle is deactivated. */
    public static void release(UUID owner) {
        PENDING_ACTIVATIONS.remove(owner);
        for (ServerLevel level : Set.copyOf(WINDOWS_BY_LEVEL.keySet())) {
            release(level, owner);
        }
    }

    static Set<ChunkPos> windowAround(ChunkPos center) {
        return windowAround(center, 1);
    }

    static Set<ChunkPos> windowAround(ChunkPos center, int radius) {
        Set<ChunkPos> chunks = new HashSet<>();
        for (int x = center.x() - radius; x <= center.x() + radius; x++) {
            for (int z = center.z() - radius; z <= center.z() + radius; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        return Set.copyOf(chunks);
    }

    private static void validateTickets(ServerLevel level, TicketHelper helper) {
        FarAndWideSavedData data = FarAndWideSavedData.get(level.getServer());
        Map<UUID, Set<ChunkPos>> validWindows = WINDOWS_BY_LEVEL.computeIfAbsent(level, ignored -> new HashMap<>());

        helper.getEntityTickets().forEach((owner, ticketSet) -> {
            int assigneeId = data.getVehicleAssigneeId(owner);
            RouteAssignment assignment = data.getAssignment(assigneeId);
            if (assignment == null || !assignment.isActive()) {
                helper.removeAllTickets(owner);
                return;
            }
            if (!canTrack(isTracked(owner), trackedVehicleCount(), Config.MAX_CHUNK_LOADED_VEHICLES.get())) {
                helper.removeAllTickets(owner);
                data.setAssignmentActive(assigneeId, false);
                FarAndWide.LOGGER.warn("Paused restored route vehicle {} because the chunk-loading limit is {}",
                        owner, Config.MAX_CHUNK_LOADED_VEHICLES.get());
                return;
            }

            Set<ChunkPos> chunks = new HashSet<>();
            ticketSet.normal().forEach(chunk -> chunks.add(ChunkPos.unpack(chunk)));
            validWindows.put(owner, Set.copyOf(chunks));
            ticketSet.naturalSpawning().forEach(chunk -> helper.removeTicket(owner, chunk, true));
        });
        if (validWindows.isEmpty()) {
            WINDOWS_BY_LEVEL.remove(level);
        }
    }

    static boolean canTrack(boolean alreadyTracked, int trackedVehicles, int limit) {
        return alreadyTracked || trackedVehicles < limit;
    }

    private static boolean isTracked(UUID owner) {
        return WINDOWS_BY_LEVEL.values().stream().anyMatch(windows -> windows.containsKey(owner));
    }

    private static int trackedVehicleCount() {
        Set<UUID> owners = new HashSet<>();
        WINDOWS_BY_LEVEL.values().forEach(windows -> owners.addAll(windows.keySet()));
        return owners.size();
    }

    private static void releaseOtherLevels(UUID owner, ServerLevel currentLevel) {
        for (ServerLevel level : Set.copyOf(WINDOWS_BY_LEVEL.keySet())) {
            if (level != currentLevel) {
                release(level, owner);
            }
        }
    }

    private static void release(ServerLevel level, UUID owner) {
        Map<UUID, Set<ChunkPos>> levelWindows = WINDOWS_BY_LEVEL.get(level);
        if (levelWindows == null) {
            return;
        }
        Set<ChunkPos> previous = levelWindows.remove(owner);
        if (previous != null) {
            for (ChunkPos chunk : previous) {
                TICKETS.forceChunk(level, owner, chunk.x(), chunk.z(), false, false);
            }
        }
        if (levelWindows.isEmpty()) {
            WINDOWS_BY_LEVEL.remove(level);
        }
    }

    private static void installWindow(ServerLevel level, UUID owner, Set<ChunkPos> desired) {
        Map<UUID, Set<ChunkPos>> levelWindows = WINDOWS_BY_LEVEL.computeIfAbsent(level, ignored -> new HashMap<>());
        Set<ChunkPos> previous = levelWindows.getOrDefault(owner, Set.of());
        for (ChunkPos chunk : desired) {
            if (!previous.contains(chunk)) {
                TICKETS.forceChunk(level, owner, chunk.x(), chunk.z(), true, false);
            }
        }
        for (ChunkPos chunk : previous) {
            if (!desired.contains(chunk)) {
                TICKETS.forceChunk(level, owner, chunk.x(), chunk.z(), false, false);
            }
        }
        levelWindows.put(owner, desired);
    }

    private static void rememberLocation(Entity vehicle, boolean exact) {
        if (!(vehicle.level() instanceof ServerLevel level)
                || !vehicle.hasData(FarAndWideAttachments.ASSIGNEE_ID.get())) {
            return;
        }
        FarAndWideSavedData data = FarAndWideSavedData.get(level.getServer());
        FarAndWideSavedData.VehicleLocation previous = data.getVehicleLocation(vehicle.getUUID()).orElse(null);
        Identifier dimension = level.dimension().identifier();
        if (exact || previous == null || !previous.dimension().equals(dimension)
                || !chunkAt(previous.position()).equals(vehicle.chunkPosition())) {
            data.updateVehicleLocation(vehicle.getUUID(), dimension, vehicle.blockPosition());
        }
    }

    private static ChunkPos chunkAt(net.minecraft.core.BlockPos position) {
        return new ChunkPos(position.getX() >> 4, position.getZ() >> 4);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        for (Map.Entry<UUID, PendingActivation> entry : Map.copyOf(PENDING_ACTIVATIONS).entrySet()) {
            UUID owner = entry.getKey();
            PendingActivation pending = entry.getValue();
            FarAndWideSavedData data = FarAndWideSavedData.get(event.getServer());
            RouteAssignment assignment = data.getAssignment(pending.assigneeId());
            if (assignment == null || !assignment.isActive()) {
                release(owner);
                continue;
            }

            Entity entity = pending.level().getEntity(owner);
            if (entity != null) {
                PENDING_ACTIVATIONS.remove(owner);
                update(entity, pending.assigneeId());
                RouteNetwork.broadcastLoadedVehicleAssignment(
                        event.getServer(), entity, data.getAssignment(pending.assigneeId()));
            } else if (pending.ticksRemaining() <= 1) {
                release(owner);
                data.setAssignmentActive(pending.assigneeId(), false);
                RouteNetwork.reportVehicleActivationFailure(
                        event.getServer(), pending.requestingPlayer(), RouteOperationResult.VEHICLE_NOT_FOUND);
            } else {
                PENDING_ACTIVATIONS.put(owner, pending.tick());
            }
        }
    }

    /** Writes a compact server-side diagnostic summary without exposing mutable state. */
    public static void logStatus() {
        int windows = trackedVehicleCount();
        int chunkOwnerships = WINDOWS_BY_LEVEL.values().stream()
                .flatMap(levelWindows -> levelWindows.values().stream())
                .mapToInt((@NonNull Set<ChunkPos> window) -> window.size())
                .sum();
        FarAndWide.LOGGER.info("Vehicle chunk loading: {} tracked vehicles, {} chunk ownerships, radius {}, limit {}",
                windows, chunkOwnerships, Config.VEHICLE_CHUNK_RADIUS.get(),
                Config.MAX_CHUNK_LOADED_VEHICLES.get());
    }

    private static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        if (!(event.getLevel() instanceof ServerLevel level)
                || entity.getRemovalReason() == null
                || !entity.getRemovalReason().shouldDestroy()
                || !ServerVehicleController.supports(entity)
                || !entity.hasData(FarAndWideAttachments.ASSIGNEE_ID.get())) {
            return;
        }
        release(entity);
        FarAndWideSavedData data = FarAndWideSavedData.get(level.getServer());
        if (data.removeAssignment(entity.getData(FarAndWideAttachments.ASSIGNEE_ID.get()))) {
            RouteNetwork.broadcastVehicleRemoval(level.getServer(), entity.getId());
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        // TicketController persistence is responsible for loading active windows
        // after restart. Runtime Entity and ServerLevel references must not leak.
        WINDOWS_BY_LEVEL.clear();
        PENDING_ACTIVATIONS.clear();
    }

    private record PendingActivation(
            ServerLevel level, int assigneeId, UUID requestingPlayer, int ticksRemaining) {
        PendingActivation tick() {
            return new PendingActivation(level, assigneeId, requestingPlayer, ticksRemaining - 1);
        }
    }
}
