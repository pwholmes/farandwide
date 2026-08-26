package com.lastcallsoftware.farandwide.route.network.payload;

import com.lastcallsoftware.farandwide.route.RouteOperationResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RouteOperationResultPayload(RouteOperationResult result) implements CustomPacketPayload {
    public static final Type<RouteOperationResultPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("farandwide", "route_operation_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RouteOperationResultPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeVarInt(payload.result.ordinal()),
            buffer -> {
                int ordinal = buffer.readVarInt();
                if (ordinal < 0 || ordinal >= RouteOperationResult.values().length) {
                    throw new IllegalArgumentException("Unknown route operation result");
                }
                return new RouteOperationResultPayload(RouteOperationResult.values()[ordinal]);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
