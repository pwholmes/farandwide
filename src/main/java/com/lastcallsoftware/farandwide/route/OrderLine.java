package com.lastcallsoftware.farandwide.route;

import com.lastcallsoftware.farandwide.Constants;
import java.util.Objects;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.eclipse.jdt.annotation.NonNullByDefault;

/** One exact item-and-components variant and its cumulative delivery count. */
@NonNullByDefault
public record OrderLine(ItemResource resource, int requested, int delivered) {
    public OrderLine {
        Objects.requireNonNull(resource);
        if (resource.isEmpty() || requested <= 0 || requested > Constants.Orders.MAX_QUANTITY
                || delivered < 0 || delivered > requested) {
            throw new IllegalArgumentException("Invalid order quantity");
        }
    }

    /** Compatibility constructor for ordinary item identifiers used by old saves and callers. */
    public OrderLine(Identifier itemId, int requested, int delivered) {
        this(ItemResource.of(BuiltInRegistries.ITEM.getValue(itemId)), requested, delivered);
    }

    public Identifier itemId() { return BuiltInRegistries.ITEM.getKey(resource.getItem()); }

    public int remaining() {
        return requested - delivered;
    }
}
