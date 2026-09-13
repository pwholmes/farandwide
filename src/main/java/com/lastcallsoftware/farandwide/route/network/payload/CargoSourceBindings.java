package com.lastcallsoftware.farandwide.route.network.payload;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.route.CargoStationBinding;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import org.eclipse.jdt.annotation.NonNullByDefault;

/** Shared, bounded wire format for order source inventory links. */
@NonNullByDefault
final class CargoSourceBindings {
    private CargoSourceBindings() {}

    static void write(FriendlyByteBuf buffer, List<CargoStationBinding> sources) {
        if (sources.size() > Constants.Orders.MAX_SOURCE_INVENTORIES) throw new IllegalArgumentException("Too many sources");
        buffer.writeVarInt(sources.size());
        sources.forEach(source -> {
            buffer.writeBlockPos(source.position());
            buffer.writeEnum(source.accessSide());
        });
    }

    static List<CargoStationBinding> read(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > Constants.Orders.MAX_SOURCE_INVENTORIES) throw new IllegalArgumentException("Too many sources");
        List<CargoStationBinding> sources = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            BlockPos position = buffer.readBlockPos();
            sources.add(new CargoStationBinding(position, buffer.readEnum(Direction.class)));
        }
        return List.copyOf(sources);
    }
}
