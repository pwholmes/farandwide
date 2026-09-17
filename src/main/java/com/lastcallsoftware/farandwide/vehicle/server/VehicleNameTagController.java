package com.lastcallsoftware.farandwide.vehicle.server;

import com.lastcallsoftware.farandwide.route.network.RouteNetwork;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideSavedData;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.NameTagItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Gives boats and minecarts the same resource-backed custom-name interaction as living entities. */
public final class VehicleNameTagController {
    private VehicleNameTagController() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(VehicleNameTagController::onEntityInteract);
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Entity vehicle = event.getTarget();
        if (!(vehicle instanceof AbstractBoat || vehicle instanceof AbstractMinecart) || !vehicle.isAlive()) {
            return;
        }
        ItemStack heldItem = event.getItemStack();
        Component customName = heldItem.get(DataComponents.CUSTOM_NAME);
        if (!(heldItem.getItem() instanceof NameTagItem) || customName == null
                || !vehicle.getType().canSerialize()) {
            return;
        }

        // Consume the interaction on both sides so using the tag does not also
        // mount or open the vehicle. The server owns the name and item-count mutation.
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        if (vehicle.level().isClientSide()) {
            return;
        }

        vehicle.setCustomName(customName);
        heldItem.shrink(1);
        if (vehicle.level() instanceof ServerLevel level) {
            FarAndWideSavedData.get(level.getServer())
                    .updateVehicleCustomName(vehicle.getUUID(), customName.getString());
            RouteNetwork.broadcastVehicleAssignments(level.getServer());
        }
    }
}
