package com.lastcallsoftware.farandwide.route.network.payload;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.route.CargoStationBinding;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import org.eclipse.jdt.annotation.NonNullByDefault;

/** Shared, bounded wire format for source inventory links. */
@NonNullByDefault
final class SourceInventoryBindings {
    private SourceInventoryBindings() {}

    static void write(FriendlyByteBuf buffer, List<CargoStationBinding> sourceInventories) {
        if (sourceInventories.size() > Constants.Orders.MAX_SOURCE_INVENTORIES) throw new IllegalArgumentException("Too many source inventories");
        buffer.writeVarInt(sourceInventories.size());
        sourceInventories.forEach(sourceInventory -> {
            buffer.writeBlockPos(sourceInventory.position());
            buffer.writeEnum(sourceInventory.accessSide());
        });
    }

    static List<CargoStationBinding> read(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > Constants.Orders.MAX_SOURCE_INVENTORIES) throw new IllegalArgumentException("Too many source inventories");
        List<CargoStationBinding> sourceInventories = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            BlockPos position = buffer.readBlockPos();
            sourceInventories.add(new CargoStationBinding(position, buffer.readEnum(Direction.class)));
        }
        return List.copyOf(sourceInventories);
    }
}
