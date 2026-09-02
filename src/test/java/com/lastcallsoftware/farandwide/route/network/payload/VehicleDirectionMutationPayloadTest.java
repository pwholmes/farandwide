package com.lastcallsoftware.farandwide.route.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class VehicleDirectionMutationPayloadTest {
    @Test
    void wireMutationPreservesAssignee() {
        VehicleDirectionMutationPayload sent = new VehicleDirectionMutationPayload(42);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);

        VehicleDirectionMutationPayload.STREAM_CODEC.encode(buffer, sent);

        assertEquals(sent, VehicleDirectionMutationPayload.STREAM_CODEC.decode(buffer));
    }
}
