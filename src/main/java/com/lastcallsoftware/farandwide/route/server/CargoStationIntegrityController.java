package com.lastcallsoftware.farandwide.route.server;

import com.lastcallsoftware.farandwide.route.network.OrderNetwork;
import com.lastcallsoftware.farandwide.route.network.RouteNetwork;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/** Repairs cargo waypoints only when their bound block is actually broken; no periodic station scan is needed. */
public final class CargoStationIntegrityController {
    private CargoStationIntegrityController() {}
    public static void register() { NeoForge.EVENT_BUS.addListener(CargoStationIntegrityController::onBreak); }

    private static void onBreak(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) || event.isCanceled()) return;
        var repair = FarAndWideSavedData.get(level.getServer()).repairDestroyedStation(level.dimension().identifier(), event.getPos());
        if (!repair.changed()) return;
        for (var entry : repair.waypointsByRoute().entrySet()) {
            var route = FarAndWideSavedData.get(level.getServer()).getRoute(entry.getKey());
            String name = route == null ? "?" : route.getName();
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                    "Cargo station removed from Route '" + name + "' (" + entry.getValue().size() + " waypoint(s) adjusted)."), false);
        }
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (repair.affectedOrderOwners().contains(player.getUUID())) {
                player.sendSystemMessage(Component.literal("Order tracking was removed because a cargo station on its journey was destroyed."));
            }
        }
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            for (int routeId : repair.waypointsByRoute().keySet()) {
                RouteNetwork.syncRouteAfterServerMutation(player, routeId);
            }
        }
        OrderNetwork.broadcastOrders(level.getServer());
    }
}
