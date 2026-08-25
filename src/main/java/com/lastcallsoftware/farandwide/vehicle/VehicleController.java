package com.lastcallsoftware.farandwide.vehicle;

import net.minecraft.world.entity.vehicle.boat.Boat;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.route.RouteManager;
import com.lastcallsoftware.farandwide.route.RouteTraversalController;

import net.minecraft.client.Minecraft;

public class VehicleController {
    public static void register() {
        NeoForge.EVENT_BUS.addListener(VehicleController::onClientTick);
        RouteTraversalController.register();
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        for (RouteAssignment assignment : RouteManager.getAssignments()) {
            if (!assignment.isActive()) {
                continue;
            }

            if (minecraft.level.getEntity(assignment.getAssigneeEntityId()) instanceof Boat boat
                    && RouteManager.getTargetWaypoint(assignment) != null) {
                boat.setInput(false, false, true, false);
            }
        }
    }
}
