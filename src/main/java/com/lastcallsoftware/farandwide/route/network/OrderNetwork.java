package com.lastcallsoftware.farandwide.route.network;

import com.lastcallsoftware.farandwide.route.network.payload.OrderPayloads;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideSavedData;
import com.lastcallsoftware.farandwide.route.server.OrderService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.eclipse.jdt.annotation.NonNullByDefault;

/** Transport adapter for orders; tracking snapshots are visible only to their requesting player. */
@NonNullByDefault
public final class OrderNetwork {
    private OrderNetwork() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(OrderPayloads.Request.TYPE, OrderPayloads.Request.STREAM_CODEC, (payload, context) -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (payload.action() == OrderPayloads.Action.LIST) {
                sendOrders(player);
                return;
            }
            if (payload.action() == OrderPayloads.Action.AVAILABLE) {
                PacketDistributor.sendToPlayer(player, new OrderPayloads.AvailableItems(payload.routeId(), payload.originId(),
                        OrderService.availableItems(player, payload.routeId(), payload.originId()).stream()
                                .map(item -> new OrderPayloads.AvailableItem(item.resource(), item.quantity())).toList()));
                return;
            }
            OrderService.Outcome outcome = payload.action() == OrderPayloads.Action.PLACE
                    ? OrderService.place(player, payload.id(), new com.lastcallsoftware.farandwide.route.OrderJourney(payload.journeyLegs()), payload.lines())
                    : OrderService.cancel(player, payload.id());
            sendOrders(player);
            context.reply(new OrderPayloads.Reply(payload.id(), outcome.result(), outcome.detail()));
            if (payload.action() == OrderPayloads.Action.PLACE
                    && outcome.result() == com.lastcallsoftware.farandwide.route.OrderResult.PLACED) {
                RouteNetwork.syncRouteAfterServerMutation(player, payload.routeId());
            }
        });
        registrar.playToClient(OrderPayloads.Snapshot.TYPE, OrderPayloads.Snapshot.STREAM_CODEC);
        registrar.playToClient(OrderPayloads.AvailableItems.TYPE, OrderPayloads.AvailableItems.STREAM_CODEC);
        registrar.playToClient(OrderPayloads.Reply.TYPE, OrderPayloads.Reply.STREAM_CODEC);
    }

    public static void sendOrders(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new OrderPayloads.Snapshot(
                FarAndWideSavedData.get(player.level().getServer()).getOrders().stream()
                        .filter(order -> order.playerId().equals(player.getUUID())).toList()));
    }

    public static void broadcastOrders(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(OrderNetwork::sendOrders);
    }
}
