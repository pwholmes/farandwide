package com.lastcallsoftware.farandwide.route.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import com.lastcallsoftware.farandwide.route.VehicleRouteAssignment;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class VehicleAssignmentsSnapshotPayloadTest {
    @Test
    void wireSnapshotPreservesCurrentLastKnownAndUnavailablePositions() {
        VehicleAssignmentsSnapshotPayload sent = new VehicleAssignmentsSnapshotPayload(List.of(
                new VehicleRouteAssignment(42, 7, "Boat 1", 3, true).withPosition(
                        Identifier.fromNamespaceAndPath("minecraft", "overworld"),
                        new BlockPos(124, 64, -38)),
                new VehicleRouteAssignment(84, 7, "Horse 1", 1).withLastKnownPosition(
                        Identifier.fromNamespaceAndPath("minecraft", "the_nether"),
                        new BlockPos(-20, 70, 18)),
                new VehicleRouteAssignment(126, 7, "Boat 2", 0),
                new VehicleRouteAssignment(168, 7, "Paul (Player)", 2, true).withPosition(
                        Identifier.fromNamespaceAndPath("minecraft", "overworld"),
                        new BlockPos(10, 65, 20))));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);

        VehicleAssignmentsSnapshotPayload.STREAM_CODEC.encode(buffer, sent);
        VehicleAssignmentsSnapshotPayload received =
                VehicleAssignmentsSnapshotPayload.STREAM_CODEC.decode(buffer);

        assertEquals(sent, received);
    }
}
