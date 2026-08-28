package com.lastcallsoftware.farandwide.route.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lastcallsoftware.farandwide.route.CargoBehavior;
import com.lastcallsoftware.farandwide.route.CargoFilter;
import com.lastcallsoftware.farandwide.route.CargoOperation;
import com.lastcallsoftware.farandwide.route.CargoStationBinding;
import com.lastcallsoftware.farandwide.route.WaypointAction;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class WaypointMutationPayloadTest {
    @Test
    void replacementRequestRoundTripPreservesStableIdAndCargoConfiguration() {
        CargoBehavior behavior = new CargoBehavior(
                CargoOperation.UNLOAD_THEN_LOAD,
                CargoFilter.allowList(List.of(Identifier.parse("minecraft:coal"))),
                CargoFilter.allowList(List.of(Identifier.parse("minecraft:iron_ingot"))),
                Optional.of(new CargoStationBinding(new BlockPos(4, 65, -8), Direction.UP)),
                Optional.of(new CargoStationBinding(new BlockPos(3, 65, -8), Direction.NORTH)));
        WaypointMutationPayload sent = new WaypointMutationPayload(
                WaypointMutationPayload.Action.REPLACE,
                12,
                34,
                new Vec3(4.5, 65, -8),
                Identifier.parse("minecraft:overworld"),
                WaypointAction.cargo(behavior),
                1,
                4.5);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);

        WaypointMutationPayload.STREAM_CODEC.encode(buffer, sent);
        WaypointMutationPayload received = WaypointMutationPayload.STREAM_CODEC.decode(buffer);

        assertEquals(sent, received);
    }

    @Test
    void deleteRequestCarriesRouteAndWaypointIds() {
        WaypointMutationPayload payload = WaypointMutationPayload.delete(7, 91);

        assertEquals(WaypointMutationPayload.Action.DELETE, payload.mutation());
        assertEquals(7, payload.routeId());
        assertEquals(91, payload.waypointId());
    }
}
