package com.lastcallsoftware.farandwide.route.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class VehicleUnassignmentMutationPayloadTest {
    @Test
    void wireMutationPreservesAssignee() {
        VehicleUnassignmentMutationPayload sent = new VehicleUnassignmentMutationPayload(42);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);

        VehicleUnassignmentMutationPayload.STREAM_CODEC.encode(buffer, sent);

        assertEquals(sent, VehicleUnassignmentMutationPayload.STREAM_CODEC.decode(buffer));
    }
}
