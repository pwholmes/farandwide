package com.lastcallsoftware.farandwide.route;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** A specific block inventory and access face for one direction of a cargo transfer. */
public record CargoStationBinding(BlockPos position, Direction accessSide) {
    public CargoStationBinding {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(accessSide, "accessSide");
    }
}
