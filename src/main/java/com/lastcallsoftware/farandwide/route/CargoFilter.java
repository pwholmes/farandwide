package com.lastcallsoftware.farandwide.route;

import com.lastcallsoftware.farandwide.Constants;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.Identifier;
import org.eclipse.jdt.annotation.NonNullByDefault;

/** Item selection for one direction of a cargo transfer. */
@NonNullByDefault
public record CargoFilter(Mode mode, List<Identifier> itemIds) {
    public CargoFilter {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(itemIds, "itemIds");
        if (itemIds.size() > Constants.Network.MAX_FILTER_ITEMS) {
            throw new IllegalArgumentException("A cargo filter cannot contain more than "
                    + Constants.Network.MAX_FILTER_ITEMS + " item IDs");
        }
        itemIds = List.copyOf(new LinkedHashSet<>(itemIds));
        if (mode == Mode.ALL && !itemIds.isEmpty()) {
            throw new IllegalArgumentException("An all-items filter cannot contain item IDs");
        }
        if (mode == Mode.ALLOW_LIST && itemIds.isEmpty()) {
            throw new IllegalArgumentException("An allow-list cargo filter must contain at least one item ID");
        }
    }

    public static CargoFilter all() {
        return new CargoFilter(Mode.ALL, List.of());
    }

    public static CargoFilter allowList(List<Identifier> itemIds) {
        return new CargoFilter(Mode.ALLOW_LIST, itemIds);
    }

    /** Returns whether this filter permits the supplied item registry identifier. */
    public boolean allows(Identifier itemId) {
        Objects.requireNonNull(itemId, "itemId");
        return mode == Mode.ALL || itemIds.contains(itemId);
    }

    public boolean isAll() {
        return mode == Mode.ALL;
    }

    public enum Mode {
        ALL,
        ALLOW_LIST
    }
}
