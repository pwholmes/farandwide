package com.lastcallsoftware.farandwide.route.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class AssignmentMutationPayloadTest {
    @Test
    void toggleVehicleActionSurvivesWireRoundTrip() {
        AssignmentMutationPayload sent = new AssignmentMutationPayload(
                AssignmentMutationPayload.Action.TOGGLE_VEHICLE, 0);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);

        AssignmentMutationPayload.STREAM_CODEC.encode(buffer, sent);
        AssignmentMutationPayload received = AssignmentMutationPayload.STREAM_CODEC.decode(buffer);

        assertEquals(AssignmentMutationPayload.Action.TOGGLE_VEHICLE, received.action());
        assertEquals(0, received.routeId());
    }

    @Test
    void reverseVehicleActionSurvivesWireRoundTrip() {
        AssignmentMutationPayload sent = new AssignmentMutationPayload(
                AssignmentMutationPayload.Action.REVERSE_VEHICLE, 0);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);

        AssignmentMutationPayload.STREAM_CODEC.encode(buffer, sent);
        AssignmentMutationPayload received = AssignmentMutationPayload.STREAM_CODEC.decode(buffer);

        assertEquals(AssignmentMutationPayload.Action.REVERSE_VEHICLE, received.action());
        assertEquals(0, received.routeId());
    }
}
