package com.lastcallsoftware.farandwide.route.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class RouteActivationMutationPayloadTest {
    @Test
    void wireMutationPreservesRouteAndExplicitState() {
        RouteActivationMutationPayload sent = new RouteActivationMutationPayload(7, true);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);

        RouteActivationMutationPayload.STREAM_CODEC.encode(buffer, sent);

        assertEquals(sent, RouteActivationMutationPayload.STREAM_CODEC.decode(buffer));
    }
}
