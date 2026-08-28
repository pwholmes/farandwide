package com.lastcallsoftware.farandwide.vehicle.client;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.vehicle.HorseControls;
import com.lastcallsoftware.farandwide.vehicle.VehicleActuator;
import com.lastcallsoftware.farandwide.vehicle.navigation.NavigationIntent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Applies auto-navigation input when the route is assigned directly to the player. */
final class ClientPlayerActuator implements VehicleActuator {
    @Override
    public boolean supports(Entity vehicle) {
        return vehicle instanceof Player;
    }

    @Override
    public boolean isAuthoritative(Entity vehicle) {
        return Minecraft.getInstance().player == vehicle;
    }

    @Override
    public void apply(Entity vehicle, NavigationIntent intent) {
        Player player = (Player) vehicle;
        HorseControls.Input input = HorseControls.from(
                player.getYRot(), intent, Constants.Vehicles.PLAYER_MOVES_WHILE_TURNING);
        player.setYRot(input.yaw());
        if (input.forward() > 0.0F
                && player instanceof LocalPlayer localPlayer
                && localPlayer.onGround()
                && localPlayer.horizontalCollision) {
            // Vanilla's predictive auto-jump only observes keyboard input. Auto-nav
            // instead uses the same normal jump physics once forward motion is blocked.
            localPlayer.jumpFromGround();
        }
    }

    @Override
    public void stop(Entity vehicle) {
        // Movement input is injected only while an active assignment exists, so
        // omitting it on the next input update restores normal keyboard control.
    }
}
