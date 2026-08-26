package com.lastcallsoftware.farandwide.route.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Requests an assignment change for the sender or the vehicle they are riding. */
/**
 * Client request to assign a route or toggle the current assignment.
 *
 * <p>The server, not the client, decides whether the assignee is the player or
 * their ridden entity. {@code routeId} is used only for {@link Action#ASSIGN};
 * toggle requests send zero. Action ordinals are part of the current wire format.
 */
public record AssignmentMutationPayload(Action action, int routeId) implements CustomPacketPayload {
    public static final Type<AssignmentMutationPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("farandwide", "assignment_mutation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssignmentMutationPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> { buffer.writeVarInt(payload.action.ordinal()); buffer.writeVarInt(payload.routeId); },
            buffer -> {
                int action = buffer.readVarInt();
                if (action < 0 || action >= Action.values().length) throw new IllegalArgumentException("Invalid assignment action");
                return new AssignmentMutationPayload(Action.values()[action], buffer.readVarInt());
            });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public enum Action { ASSIGN, TOGGLE_ACTIVE }
}
