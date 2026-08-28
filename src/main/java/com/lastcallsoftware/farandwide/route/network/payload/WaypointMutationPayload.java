package com.lastcallsoftware.farandwide.route.network.payload;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.route.CargoBehavior;
import com.lastcallsoftware.farandwide.route.CargoFilter;
import com.lastcallsoftware.farandwide.route.CargoOperation;
import com.lastcallsoftware.farandwide.route.CargoStationBinding;
import com.lastcallsoftware.farandwide.route.Waypoint;
import com.lastcallsoftware.farandwide.route.WaypointAction;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** A client request for one server-authoritative stable-ID waypoint mutation. */
public record WaypointMutationPayload(
        Action mutation,
        int routeId,
        int waypointId,
        Vec3 position,
        Identifier dimension,
        WaypointAction waypointAction,
        int targetPosition,
        double arrivalRadius) implements CustomPacketPayload {
    public static final Type<WaypointMutationPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("farandwide", "waypoint_mutation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WaypointMutationPayload> STREAM_CODEC = StreamCodec.of(
            WaypointMutationPayload::write, WaypointMutationPayload::read);

    public static WaypointMutationPayload create(int routeId, Vec3 position, Identifier dimension,
            WaypointAction action) {
        return create(routeId, position, dimension, action, Constants.Waypoints.DEFAULT_ARRIVAL_RADIUS);
    }

    public static WaypointMutationPayload create(int routeId, Vec3 position, Identifier dimension,
            WaypointAction action, double arrivalRadius) {
        return new WaypointMutationPayload(Action.CREATE, routeId, 0, position, dimension, action, -1,
                arrivalRadius);
    }

    public static WaypointMutationPayload replace(int routeId, Waypoint waypoint, int targetPosition) {
        return new WaypointMutationPayload(Action.REPLACE, routeId, waypoint.id(), waypoint.position(),
                waypoint.dimension(), waypoint.action(), targetPosition, waypoint.arrivalRadius());
    }

    public static WaypointMutationPayload convert(int routeId, int waypointId, WaypointAction action) {
        return new WaypointMutationPayload(
                Action.CONVERT, routeId, waypointId, Vec3.ZERO, Waypoint.DEFAULT_DIMENSION, action, -1,
                Constants.Waypoints.DEFAULT_ARRIVAL_RADIUS);
    }

    public static WaypointMutationPayload delete(int routeId, int waypointId) {
        return new WaypointMutationPayload(
                Action.DELETE, routeId, waypointId, Vec3.ZERO, Waypoint.DEFAULT_DIMENSION,
                WaypointAction.normal(), -1, Constants.Waypoints.DEFAULT_ARRIVAL_RADIUS);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, WaypointMutationPayload payload) {
        buffer.writeVarInt(payload.mutation.ordinal());
        buffer.writeVarInt(payload.routeId);
        buffer.writeVarInt(payload.waypointId);
        buffer.writeDouble(payload.position.x);
        buffer.writeDouble(payload.position.y);
        buffer.writeDouble(payload.position.z);
        buffer.writeUtf(payload.dimension.toString(), Constants.Network.MAX_IDENTIFIER_LENGTH);
        writeAction(buffer, payload.waypointAction);
        buffer.writeInt(payload.targetPosition);
        buffer.writeDouble(payload.arrivalRadius);
    }

    private static WaypointMutationPayload read(RegistryFriendlyByteBuf buffer) {
        Action mutation = readEnum(buffer, Action.values(), "waypoint mutation");
        int routeId = buffer.readVarInt();
        int waypointId = buffer.readVarInt();
        Vec3 position = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        Identifier dimension = Identifier.parse(buffer.readUtf(Constants.Network.MAX_IDENTIFIER_LENGTH));
        WaypointAction action = readAction(buffer);
        return new WaypointMutationPayload(
                mutation, routeId, waypointId, position, dimension, action, buffer.readInt(), buffer.readDouble());
    }

    private static void writeAction(RegistryFriendlyByteBuf buffer, WaypointAction action) {
        if (action instanceof WaypointAction.Cargo cargo) {
            buffer.writeBoolean(true);
            CargoBehavior behavior = cargo.behavior();
            buffer.writeVarInt(behavior.operation().ordinal());
            writeFilter(buffer, behavior.loadFilter());
            writeFilter(buffer, behavior.unloadFilter());
            writeStation(buffer, behavior.loadStation());
            writeStation(buffer, behavior.unloadStation());
        } else {
            buffer.writeBoolean(false);
        }
    }

    private static WaypointAction readAction(RegistryFriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return WaypointAction.normal();
        }
        CargoOperation operation = readEnum(buffer, CargoOperation.values(), "cargo operation");
        return WaypointAction.cargo(new CargoBehavior(
                operation, readFilter(buffer), readFilter(buffer), readStation(buffer), readStation(buffer)));
    }

    private static void writeStation(RegistryFriendlyByteBuf buffer, java.util.Optional<CargoStationBinding> station) {
        buffer.writeBoolean(station.isPresent());
        station.ifPresent(binding -> {
            buffer.writeInt(binding.position().getX());
            buffer.writeInt(binding.position().getY());
            buffer.writeInt(binding.position().getZ());
            buffer.writeVarInt(binding.accessSide().ordinal());
        });
    }

    private static java.util.Optional<CargoStationBinding> readStation(RegistryFriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return java.util.Optional.empty();
        }
        BlockPos position = new BlockPos(buffer.readInt(), buffer.readInt(), buffer.readInt());
        Direction side = readEnum(buffer, Direction.values(), "cargo station side");
        return java.util.Optional.of(new CargoStationBinding(position, side));
    }

    private static void writeFilter(RegistryFriendlyByteBuf buffer, CargoFilter filter) {
        if (filter.itemIds().size() > Constants.Network.MAX_FILTER_ITEMS) {
            throw new IllegalArgumentException("Cargo filter exceeds request limits");
        }
        buffer.writeVarInt(filter.mode().ordinal());
        buffer.writeVarInt(filter.itemIds().size());
        filter.itemIds().forEach(itemId -> buffer.writeUtf(itemId.toString(), Constants.Network.MAX_IDENTIFIER_LENGTH));
    }

    private static CargoFilter readFilter(RegistryFriendlyByteBuf buffer) {
        CargoFilter.Mode mode = readEnum(buffer, CargoFilter.Mode.values(), "cargo filter mode");
        int itemCount = buffer.readVarInt();
        if (itemCount < 0 || itemCount > Constants.Network.MAX_FILTER_ITEMS) {
            throw new IllegalArgumentException("Invalid cargo filter size");
        }
        List<Identifier> itemIds = new ArrayList<>(itemCount);
        for (int index = 0; index < itemCount; index++) {
            itemIds.add(Identifier.parse(buffer.readUtf(Constants.Network.MAX_IDENTIFIER_LENGTH)));
        }
        return new CargoFilter(mode, itemIds);
    }

    private static <E extends Enum<E>> E readEnum(RegistryFriendlyByteBuf buffer, E[] values, String description) {
        int ordinal = buffer.readVarInt();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Unknown " + description);
        }
        return values[ordinal];
    }

    public enum Action {
        CREATE,
        REPLACE,
        CONVERT,
        DELETE
    }
}
