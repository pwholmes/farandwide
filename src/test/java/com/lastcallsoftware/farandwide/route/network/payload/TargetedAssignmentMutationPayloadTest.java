package com.lastcallsoftware.farandwide.route.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class TargetedAssignmentMutationPayloadTest {
    @Test
    void routeAndEntitySurviveWireRoundTrip() {
        TargetedAssignmentMutationPayload sent = new TargetedAssignmentMutationPayload(42, 75);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);

        TargetedAssignmentMutationPayload.STREAM_CODEC.encode(buffer, sent);

        assertEquals(sent, TargetedAssignmentMutationPayload.STREAM_CODEC.decode(buffer));
    }
}
