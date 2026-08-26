package com.lastcallsoftware.farandwide.route.server;

import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.route.network.RouteNetwork;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideAttachments;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideSavedData;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMountEvent;

/** Applies the assignment ownership rules when a player mounts or dismounts. */
public final class ServerMountTransitionController {
    private ServerMountTransitionController() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ServerMountTransitionController::onMountChanged);
    }

    private static void onMountChanged(EntityMountEvent event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntityMounting() instanceof ServerPlayer player)
                || event.getEntityBeingMounted() == null) {
            return;
        }

        Entity vehicle = event.getEntityBeingMounted();
        FarAndWideSavedData data = FarAndWideSavedData.get(player.level().getServer());
        int playerAssigneeId = FarAndWideAttachments.getOrCreateAssigneeId(player, data);
        int vehicleAssigneeId = FarAndWideAttachments.getOrCreateAssigneeId(vehicle, data);
        RouteAssignment vehicleAssignment = data.getAssignment(vehicleAssigneeId);

        if (event.isMounting()) {
            if (vehicleAssignment == null) {
                data.transferAssignment(playerAssigneeId, vehicleAssigneeId);
                vehicleAssignment = data.getAssignment(vehicleAssigneeId);
            } else {
                data.removeAssignment(playerAssigneeId);
            }
            if (vehicleAssignment != null) {
                data.setSelectedRouteId(playerAssigneeId, vehicleAssignment.getRouteId());
            }
        } else if (vehicleAssignment != null) {
            data.setSelectedRouteId(playerAssigneeId, vehicleAssignment.getRouteId());
        }

        RouteNetwork.syncMountTransition(player, vehicle);
    }
}
