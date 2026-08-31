package com.lastcallsoftware.farandwide.route.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class VehicleActivationMutationPayloadTest {
    @Test
    void wireMutationPreservesAssigneeAndExplicitState() {
        VehicleActivationMutationPayload sent = new VehicleActivationMutationPayload(42, true);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);

        VehicleActivationMutationPayload.STREAM_CODEC.encode(buffer, sent);

        assertEquals(sent, VehicleActivationMutationPayload.STREAM_CODEC.decode(buffer));
    }
}
