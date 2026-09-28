package com.lastcallsoftware.farandwide.route.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Transient server status for the HUD subject's timed waypoint dwell. */
public record VehicleDwellPayload(int entityId, boolean dwelling) implements CustomPacketPayload {
    public static final Type<VehicleDwellPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("farandwide", "vehicle_dwell"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VehicleDwellPayload> STREAM_CODEC = StreamCodec.of(
            VehicleDwellPayload::write, VehicleDwellPayload::read);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(RegistryFriendlyByteBuf buffer, VehicleDwellPayload payload) {
        buffer.writeVarInt(payload.entityId);
        buffer.writeBoolean(payload.dwelling);
    }

    private static VehicleDwellPayload read(RegistryFriendlyByteBuf buffer) {
        return new VehicleDwellPayload(buffer.readVarInt(), buffer.readBoolean());
    }
}
