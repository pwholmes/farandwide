package com.lastcallsoftware.farandwide.route.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.route.TraversalType;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class AssignmentSnapshotPayloadTest {
    @Test
    void wireSnapshotUsesRuntimeEntityIdForClientAssignment() {
        int persistentAssigneeId = 42;
        int runtimeEntityId = 9001;
        RouteAssignment persisted = new RouteAssignment(
                7, persistentAssigneeId, 3, -1, TraversalType.LOOP, false, true);
        AssignmentSnapshotPayload sent = new AssignmentSnapshotPayload(runtimeEntityId, persisted);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);

        AssignmentSnapshotPayload.STREAM_CODEC.encode(buffer, sent);
        AssignmentSnapshotPayload received = AssignmentSnapshotPayload.STREAM_CODEC.decode(buffer);

        assertEquals(runtimeEntityId, received.entityId());
        assertEquals(persistentAssigneeId, received.stableAssigneeId());
        assertEquals(runtimeEntityId, received.assignment().getAssigneeId());
        assertEquals(7, received.assignment().getRouteId());
        assertEquals(3, received.assignment().getTargetWaypointIndex());
        assertEquals(-1, received.assignment().getTraversalDirection());
        assertEquals(TraversalType.LOOP, received.assignment().getTraversalTypeOverride());
        assertFalse(received.assignment().isActive());
        assertTrue(received.assignment().isRestartAnchor());
    }
}
