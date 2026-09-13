package com.lastcallsoftware.farandwide.route.client;

import com.lastcallsoftware.farandwide.Constants;
import java.util.LinkedHashMap;
import java.util.Map;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/** Client-only quantities behind the order screen's two virtual inventories. */
@NonNullByDefault
final class OrderItemSelectionState {
    enum Side { AVAILABLE, SELECTED }

    private final Map<ItemResource, Integer> available = new LinkedHashMap<>();
    private final Map<ItemResource, Integer> selected = new LinkedHashMap<>();
    private @Nullable Carried carried;

    OrderItemSelectionState(Map<ItemResource, Integer> stock, Map<ItemResource, Integer> initialSelection) {
        stock.forEach((resource, quantity) -> {
            int selectedQuantity = Math.min(Math.min(initialSelection.getOrDefault(resource, 0), quantity),
                    Constants.Orders.MAX_QUANTITY);
            put(selected, resource, selectedQuantity);
            put(available, resource, quantity - selectedQuantity);
        });
    }

    Map<ItemResource, Integer> available() { return Map.copyOf(available); }
    Map<ItemResource, Integer> selected() { return Map.copyOf(selected); }
    @Nullable Carried carried() { return carried; }

    void click(Side side, @Nullable ItemResource resource, int mouseButton) {
        if (mouseButton != 0 && mouseButton != 1) return;
        Map<ItemResource, Integer> target = quantities(side);
        if (carried == null) {
            if (resource == null) return;
            int quantity = target.getOrDefault(resource, 0);
            if (quantity <= 0) return;
            int taken = mouseButton == 1 ? (quantity + 1) / 2 : quantity;
            put(target, resource, quantity - taken);
            carried = new Carried(resource, taken, side);
            return;
        }
        if (resource != null && !resource.equals(carried.resource())) {
            if (mouseButton == 1 || capacity(side, carried.resource(), target.getOrDefault(carried.resource(), 0))
                    < carried.quantity()) return;
            int clickedQuantity = target.getOrDefault(resource, 0);
            put(target, resource, 0);
            put(target, carried.resource(), target.getOrDefault(carried.resource(), 0) + carried.quantity());
            carried = new Carried(resource, clickedQuantity, side);
            return;
        }
        int current = target.getOrDefault(carried.resource(), 0);
        int capacity = capacity(side, carried.resource(), current);
        int placed = Math.min(mouseButton == 1 ? 1 : carried.quantity(), capacity);
        if (placed <= 0) return;
        put(target, carried.resource(), current + placed);
        carried = placed == carried.quantity() ? null
                : new Carried(carried.resource(), carried.quantity() - placed, carried.origin());
    }

    void quickMove(Side from, ItemResource resource) {
        Map<ItemResource, Integer> source = quantities(from);
        int quantity = source.getOrDefault(resource, 0);
        if (quantity <= 0) return;
        Side to = from == Side.AVAILABLE ? Side.SELECTED : Side.AVAILABLE;
        Map<ItemResource, Integer> destination = quantities(to);
        int current = destination.getOrDefault(resource, 0);
        int moved = Math.min(quantity, capacity(to, resource, current));
        put(source, resource, quantity - moved);
        put(destination, resource, current + moved);
    }

    void returnCarried() {
        if (carried == null) return;
        Map<ItemResource, Integer> origin = quantities(carried.origin());
        put(origin, carried.resource(), origin.getOrDefault(carried.resource(), 0) + carried.quantity());
        carried = null;
    }

    private int capacity(Side side, ItemResource resource, int current) {
        if (side == Side.AVAILABLE) return Integer.MAX_VALUE - current;
        if (!selected.containsKey(resource) && selected.size() >= Constants.Orders.MAX_LINES) return 0;
        return Constants.Orders.MAX_QUANTITY - current;
    }

    private Map<ItemResource, Integer> quantities(Side side) {
        return side == Side.AVAILABLE ? available : selected;
    }

    private static void put(Map<ItemResource, Integer> quantities, ItemResource resource, int quantity) {
        if (quantity > 0) quantities.put(resource, quantity);
        else quantities.remove(resource);
    }

    record Carried(ItemResource resource, int quantity, Side origin) {}
}
