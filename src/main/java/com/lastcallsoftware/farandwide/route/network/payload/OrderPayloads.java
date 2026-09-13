package com.lastcallsoftware.farandwide.route.network.payload;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.route.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.eclipse.jdt.annotation.NonNullByDefault;

/** Bounded order requests, private snapshots, and acknowledgments, independent of disk codecs. */
@NonNullByDefault
public final class OrderPayloads {
    private OrderPayloads() {}

    public enum Action { LIST, AVAILABLE, PLACE, CANCEL }

    public record Request(Action action, UUID id, int routeId, int originId, int destinationId,
            List<OrderJourney.Leg> journeyLegs, List<OrderLine> lines) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "order_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.of(
                OrderPayloads::writeRequest, OrderPayloads::readRequest);
        public Request {
            journeyLegs = List.copyOf(journeyLegs);
            lines = List.copyOf(lines);
            if (journeyLegs.size() > Constants.Orders.MAX_LEGS) throw new IllegalArgumentException("Too many order legs");
            if (lines.size() > Constants.Orders.MAX_LINES) throw new IllegalArgumentException("Too many order lines");
        }
        public Request(Action action, UUID id, int routeId, int originId, int destinationId, List<OrderLine> lines) {
            this(action, id, routeId, originId, destinationId,
                    action == Action.PLACE ? List.of(new OrderJourney.Leg(routeId, originId, destinationId)) : List.of(), lines);
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Snapshot(List<CargoOrder> orders) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "order_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> STREAM_CODEC = StreamCodec.of(
                OrderPayloads::writeSnapshot, OrderPayloads::readSnapshot);
        public Snapshot {
            orders = List.copyOf(orders);
            if (orders.size() > Constants.Orders.MAX_TRACKED_ORDERS) throw new IllegalArgumentException("Too many orders");
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Authoritative component-aware stock presently visible in an order origin's linked sources. */
    public record AvailableItems(int routeId, int originId, List<AvailableItem> items) implements CustomPacketPayload {
        public static final Type<AvailableItems> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
                Constants.MOD_ID, "order_available_items"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AvailableItems> STREAM_CODEC = StreamCodec.of(
                (buffer, snapshot) -> {
                    buffer.writeVarInt(snapshot.routeId());
                    buffer.writeVarInt(snapshot.originId());
                    buffer.writeVarInt(snapshot.items().size());
                    for (AvailableItem item : snapshot.items()) {
                        ItemResource.STREAM_CODEC.encode(buffer, item.resource());
                        buffer.writeVarInt(item.quantity());
                    }
                }, buffer -> {
                    int routeId = buffer.readVarInt();
                    int originId = buffer.readVarInt();
                    int count = readCount(buffer, Constants.Orders.MAX_AVAILABLE_ITEMS);
                    List<AvailableItem> items = new ArrayList<>(count);
                    for (int index = 0; index < count; index++) {
                        items.add(new AvailableItem(ItemResource.STREAM_CODEC.decode(buffer), buffer.readVarInt()));
                    }
                    return new AvailableItems(routeId, originId, items);
                });
        public AvailableItems {
            items = List.copyOf(items);
            if (items.size() > Constants.Orders.MAX_AVAILABLE_ITEMS) throw new IllegalArgumentException("Too many available items");
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record AvailableItem(ItemResource resource, int quantity) {
        public AvailableItem {
            if (resource.isEmpty() || quantity <= 0) throw new IllegalArgumentException("Available item must be non-empty");
        }
    }

    public record Reply(UUID id, OrderResult result, String detail) implements CustomPacketPayload {
        public static final Type<Reply> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "order_reply"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reply> STREAM_CODEC = StreamCodec.of((buffer, reply) -> {
            buffer.writeUUID(reply.id());
            buffer.writeEnum(reply.result());
            buffer.writeUtf(reply.detail(), Constants.Network.MAX_IDENTIFIER_LENGTH);
        }, buffer -> new Reply(buffer.readUUID(), buffer.readEnum(OrderResult.class),
                buffer.readUtf(Constants.Network.MAX_IDENTIFIER_LENGTH)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static void writeRequest(RegistryFriendlyByteBuf buffer, Request request) {
        buffer.writeEnum(request.action());
        buffer.writeUUID(request.id());
        if (request.action() == Action.PLACE || request.action() == Action.AVAILABLE) {
            buffer.writeVarInt(request.routeId());
            buffer.writeVarInt(request.originId());
            if (request.action() == Action.PLACE) {
                buffer.writeVarInt(request.destinationId());
                buffer.writeVarInt(request.journeyLegs().size());
                for (OrderJourney.Leg leg : request.journeyLegs()) {
                    buffer.writeVarInt(leg.routeId());
                    buffer.writeVarInt(leg.originWaypointId());
                    buffer.writeVarInt(leg.destinationWaypointId());
                }
                writeLines(buffer, request.lines());
            }
        }
    }

    private static Request readRequest(RegistryFriendlyByteBuf buffer) {
        Action action = buffer.readEnum(Action.class);
        UUID id = buffer.readUUID();
        if (action == Action.PLACE) {
            int routeId = buffer.readVarInt();
            int originId = buffer.readVarInt();
            int destinationId = buffer.readVarInt();
            int legCount = readCount(buffer, Constants.Orders.MAX_LEGS);
            List<OrderJourney.Leg> legs = new ArrayList<>(legCount);
            for (int index = 0; index < legCount; index++) {
                legs.add(new OrderJourney.Leg(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()));
            }
            return new Request(action, id, routeId, originId, destinationId, legs, readLines(buffer));
        }
        if (action == Action.AVAILABLE) {
            return new Request(action, id, buffer.readVarInt(), buffer.readVarInt(), 0, List.of());
        }
        return new Request(action, id, 0, 0, 0, List.of());
    }

    private static void writeLines(RegistryFriendlyByteBuf buffer, List<OrderLine> lines) {
        if (lines.size() > Constants.Orders.MAX_LINES) throw new IllegalArgumentException("Too many order lines");
        buffer.writeVarInt(lines.size());
        for (OrderLine line : lines) {
            ItemResource.STREAM_CODEC.encode(buffer, line.resource());
            buffer.writeVarInt(line.requested());
            buffer.writeVarInt(line.delivered());
        }
    }

    private static List<OrderLine> readLines(RegistryFriendlyByteBuf buffer) {
        int count = readCount(buffer, Constants.Orders.MAX_LINES);
        List<OrderLine> lines = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            lines.add(new OrderLine(ItemResource.STREAM_CODEC.decode(buffer), buffer.readVarInt(), buffer.readVarInt()));
        }
        return List.copyOf(lines);
    }

    private static int readCount(RegistryFriendlyByteBuf buffer, int maximum) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid order collection size");
        return count;
    }

    private static void writeSnapshot(RegistryFriendlyByteBuf buffer, Snapshot snapshot) {
        buffer.writeVarInt(snapshot.orders().size());
        for (CargoOrder order : snapshot.orders()) {
            buffer.writeUUID(order.id());
            buffer.writeUUID(order.playerId());
            buffer.writeVarInt(order.legs().size());
            for (OrderLeg leg : order.legs()) {
                buffer.writeVarInt(leg.routeId());
                buffer.writeVarInt(leg.originWaypointId());
                buffer.writeVarInt(leg.destinationWaypointId());
                buffer.writeBlockPos(leg.destinationStation().position());
                buffer.writeEnum(leg.destinationStation().accessSide());
                writeLines(buffer, leg.lines());
                buffer.writeEnum(leg.activationResult());
            }
        }
    }

    private static Snapshot readSnapshot(RegistryFriendlyByteBuf buffer) {
        int count = readCount(buffer, Constants.Orders.MAX_TRACKED_ORDERS);
        List<CargoOrder> orders = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            UUID id = buffer.readUUID();
            UUID player = buffer.readUUID();
            int legCount = readCount(buffer, Constants.Orders.MAX_LEGS);
            List<OrderLeg> legs = new ArrayList<>(legCount);
            for (int legIndex = 0; legIndex < legCount; legIndex++) {
                legs.add(new OrderLeg(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                        new CargoStationBinding(buffer.readBlockPos(), buffer.readEnum(Direction.class)),
                        readLines(buffer), buffer.readEnum(RouteOperationResult.class)));
            }
            orders.add(new CargoOrder(id, player, legs));
        }
        return new Snapshot(orders);
    }
}
