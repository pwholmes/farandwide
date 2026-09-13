package com.lastcallsoftware.farandwide.route.network.payload;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.route.*;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderPayloadsTest {
    private static OrderLine line;
    private static final CargoStationBinding STATION = new CargoStationBinding(new BlockPos(2, 64, 2), Direction.NORTH);

    @SuppressWarnings("deprecation")
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (var item : List.of(Items.AIR, Items.GOLD_INGOT)) {
            item.builtInRegistryHolder().bindComponents(DataComponents.COMMON_ITEM_COMPONENTS);
        }
        var named = Items.GOLD_INGOT.getDefaultInstance();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Networked gold"));
        line = new OrderLine(ItemResource.of(named), 4, 0);
    }

    @Test void placementAndCancellationRequestsRoundTrip() {
        var place = new OrderPayloads.Request(OrderPayloads.Action.PLACE, UUID.randomUUID(), 1, 2, 3, List.of(line));
        var cancel = new OrderPayloads.Request(OrderPayloads.Action.CANCEL, place.id(), 0, 0, 0, List.of());
        var buffer = buffer();
        try {
            OrderPayloads.Request.STREAM_CODEC.encode(buffer, place);
            OrderPayloads.Request.STREAM_CODEC.encode(buffer, cancel);
            assertEquals(place, OrderPayloads.Request.STREAM_CODEC.decode(buffer));
            assertEquals(cancel, OrderPayloads.Request.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void progressAndActivationResultRoundTrip() {
        CargoOrder order = new CargoOrder(UUID.randomUUID(), UUID.randomUUID(), 1, 2, 3, STATION,
                List.of(new OrderLine(line.resource(), 4, 2)), RouteOperationResult.CHUNK_LOADING_LIMIT);
        var snapshot = new OrderPayloads.Snapshot(List.of(order));
        var reply = new OrderPayloads.Reply(order.id(), OrderResult.INSUFFICIENT_STOCK, line.itemId().toString());
        var buffer = buffer();
        try {
            OrderPayloads.Snapshot.STREAM_CODEC.encode(buffer, snapshot);
            OrderPayloads.Reply.STREAM_CODEC.encode(buffer, reply);
            assertEquals(snapshot, OrderPayloads.Snapshot.STREAM_CODEC.decode(buffer));
            assertEquals(reply, OrderPayloads.Reply.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void rejectsOversizedRequestBeforeAllocatingLines() {
        var buffer = buffer();
        try {
            buffer.writeEnum(OrderPayloads.Action.PLACE);
            buffer.writeUUID(UUID.randomUUID());
            buffer.writeVarInt(1);
            buffer.writeVarInt(2);
            buffer.writeVarInt(3);
            buffer.writeVarInt(Constants.Orders.MAX_LINES + 1);
            assertThrows(IllegalArgumentException.class, () -> OrderPayloads.Request.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test void sourceLinksRoundTripInBothWaypointRequestsAndRouteSnapshots() {
        CargoBehavior cargo = new CargoBehavior(CargoOperation.LOAD, CargoFilter.all(), CargoFilter.all(),
                Optional.of(STATION), Optional.empty(), List.of(new CargoStationBinding(new BlockPos(4, 64, 2), Direction.WEST)));
        Waypoint waypoint = new Waypoint(2, Vec3.ZERO, Waypoint.DEFAULT_DIMENSION, WaypointAction.cargo(cargo));
        var mutation = WaypointMutationPayload.replace(1, waypoint, 0);
        var snapshot = RouteSnapshotPayload.from(List.of(new Route(1, "Supply", TraversalType.ONE_WAY, List.of(waypoint))), 1);
        var buffer = buffer();
        try {
            WaypointMutationPayload.STREAM_CODEC.encode(buffer, mutation);
            RouteSnapshotPayload.STREAM_CODEC.encode(buffer, snapshot);
            assertEquals(mutation, WaypointMutationPayload.STREAM_CODEC.decode(buffer));
            assertEquals(snapshot, RouteSnapshotPayload.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test void rejectsOversizedSourceList() {
        var buffer = buffer();
        try {
            buffer.writeVarInt(Constants.Orders.MAX_SOURCE_INVENTORIES + 1);
            assertThrows(IllegalArgumentException.class, () -> CargoSourceBindings.read(buffer));
        } finally { buffer.release(); }
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), ConnectionType.NEOFORGE);
    }
}
