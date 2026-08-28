package com.lastcallsoftware.farandwide.route.network.payload;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.route.TraversalType;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A client request to make one server-authoritative route edit. */
/**
 * Shared request envelope for mutations of route definitions and waypoints.
 *
 * <p>CREATE uses {@code name} and {@code traversalType}; UPDATE uses all fields;
 * DELETE and waypoint actions use only {@code routeId}. Unused values are transport
 * placeholders and must not be interpreted by their server action. Enum ordinals
 * are encoded on the wire, so changing their order requires a protocol-version change.
 */
public record RouteMutationPayload(Action action, int routeId, String name, TraversalType traversalType)
        implements CustomPacketPayload {
    public static final Type<RouteMutationPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("farandwide", "route_mutation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RouteMutationPayload> STREAM_CODEC = StreamCodec.of(
            RouteMutationPayload::write, RouteMutationPayload::read);

    public static RouteMutationPayload create(String name, TraversalType type) {
        return new RouteMutationPayload(Action.CREATE, 0, name, type);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(RegistryFriendlyByteBuf buffer, RouteMutationPayload payload) {
        buffer.writeVarInt(payload.action.ordinal());
        buffer.writeVarInt(payload.routeId);
        buffer.writeUtf(payload.name, Constants.Network.MAX_ROUTE_NAME_LENGTH);
        buffer.writeVarInt(payload.traversalType.ordinal());
    }

    private static RouteMutationPayload read(RegistryFriendlyByteBuf buffer) {
        int action = buffer.readVarInt();
        int routeId = buffer.readVarInt();
        String name = buffer.readUtf(Constants.Network.MAX_ROUTE_NAME_LENGTH);
        int type = buffer.readVarInt();
        if (action < 0 || action >= Action.values().length || type < 0 || type >= TraversalType.values().length) {
            throw new IllegalArgumentException("Invalid route mutation");
        }
        return new RouteMutationPayload(Action.values()[action], routeId, name, TraversalType.values()[type]);
    }

    public enum Action { CREATE, UPDATE, DELETE, ADD_WAYPOINT }
}
