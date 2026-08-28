package com.lastcallsoftware.farandwide.route;

import java.util.List;
import java.util.Objects;
import net.minecraft.resources.Identifier;

/** Item selection for one direction of a cargo transfer. */
public record CargoFilter(Mode mode, List<Identifier> itemIds) {
    public CargoFilter {
        Objects.requireNonNull(mode, "mode");
        itemIds = List.copyOf(itemIds);
        if (mode == Mode.ALL && !itemIds.isEmpty()) {
            throw new IllegalArgumentException("An all-items filter cannot contain item IDs");
        }
    }

    public static CargoFilter all() {
        return new CargoFilter(Mode.ALL, List.of());
    }

    public static CargoFilter allowList(List<Identifier> itemIds) {
        return new CargoFilter(Mode.ALLOW_LIST, itemIds);
    }

    public enum Mode {
        ALL,
        ALLOW_LIST
    }
}
