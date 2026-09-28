package com.lastcallsoftware.farandwide.route.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class VehicleDwellPayloadTest {
    @Test
    void roundTripsDwellStateForRuntimeEntity() {
        VehicleDwellPayload sent = new VehicleDwellPayload(9001, true);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);

        VehicleDwellPayload.STREAM_CODEC.encode(buffer, sent);

        assertEquals(sent, VehicleDwellPayload.STREAM_CODEC.decode(buffer));
    }
}
