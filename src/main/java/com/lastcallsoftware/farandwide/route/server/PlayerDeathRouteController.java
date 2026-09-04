package com.lastcallsoftware.farandwide.route.server;

import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.network.RouteNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Maintains one UUID-owned last-death route for each real player. */
public final class PlayerDeathRouteController {
    private PlayerDeathRouteController() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.register(PlayerDeathRouteController.class);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()
                || !(event.getEntity() instanceof ServerPlayer player)
                || player.isFakePlayer()) {
            return;
        }

        Route deathRoute = RouteService.recordPlayerDeath(player);
        RouteNetwork.syncRouteAfterServerMutation(player, deathRoute.getId());
    }
}
