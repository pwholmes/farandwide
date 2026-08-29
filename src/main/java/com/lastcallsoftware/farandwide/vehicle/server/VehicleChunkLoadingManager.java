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
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideAttachments;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideSavedData;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Maintains a small moving forced-chunk window around active route vehicles. */
public final class VehicleChunkLoadingManager {
    private static final TicketController TICKETS = new TicketController(
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "route_vehicles"),
            VehicleChunkLoadingManager::validateTickets);
    private static final Map<ServerLevel, Map<UUID, Set<ChunkPos>>> WINDOWS_BY_LEVEL = new WeakHashMap<>();

    private VehicleChunkLoadingManager() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(VehicleChunkLoadingManager::registerTicketController);
        NeoForge.EVENT_BUS.addListener(VehicleChunkLoadingManager::onServerStopped);
        NeoForge.EVENT_BUS.addListener(VehicleChunkLoadingManager::onEntityLeaveLevel);
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
        FarAndWideSavedData.get(level.getServer()).associateVehicle(owner, assigneeId);
        boolean alreadyTracked = isTracked(owner);
        if (!canTrack(alreadyTracked, trackedVehicleCount(), Config.MAX_CHUNK_LOADED_VEHICLES.get())) {
            FarAndWide.LOGGER.warn("Pausing route vehicle {} (assignee {}) because the chunk-loading limit is {}",
                    owner, assigneeId, Config.MAX_CHUNK_LOADED_VEHICLES.get());
            return false;
        }
        releaseOtherLevels(owner, level);
        Map<UUID, Set<ChunkPos>> levelWindows = WINDOWS_BY_LEVEL.computeIfAbsent(level, ignored -> new HashMap<>());
        Set<ChunkPos> previous = levelWindows.getOrDefault(owner, Set.of());
        Set<ChunkPos> desired = windowAround(vehicle.chunkPosition(), Config.VEHICLE_CHUNK_RADIUS.get());

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
        return true;
    }

    /** Releases all chunks currently tracked for a loaded entity. */
    public static void release(Entity vehicle) {
        if (!(vehicle.level() instanceof ServerLevel level)) {
            return;
        }
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

    /** Writes a compact server-side diagnostic summary without exposing mutable state. */
    public static void logStatus() {
        int windows = trackedVehicleCount();
        int chunkOwnerships = WINDOWS_BY_LEVEL.values().stream()
                .flatMap(levelWindows -> levelWindows.values().stream())
                .mapToInt(Set::size)
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
        FarAndWideSavedData.get(level.getServer()).removeAssignment(
                entity.getData(FarAndWideAttachments.ASSIGNEE_ID.get()));
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        // TicketController persistence is responsible for loading active windows
        // after restart. Runtime Entity and ServerLevel references must not leak.
        WINDOWS_BY_LEVEL.clear();
    }
}
