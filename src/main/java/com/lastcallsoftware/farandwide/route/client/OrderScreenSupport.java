package com.lastcallsoftware.farandwide.route.client;

import com.lastcallsoftware.farandwide.route.*;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/** Shared labels and endpoint choices for the two order screens. */
@NonNullByDefault
final class OrderScreenSupport {
    private OrderScreenSupport() {}

    static List<Route> eligibleRoutes() {
        return RouteManager.getRoutes().stream().filter(route -> origins(route).stream()
                .anyMatch(origin -> !destinations(route, origin).isEmpty())).toList();
    }

    static List<Waypoint> origins(@Nullable Route route) {
        return route == null ? List.of() : route.getWaypoints().stream()
                .filter(waypoint -> waypoint.action() instanceof WaypointAction.Cargo cargo
                        && cargo.behavior().operation() != CargoOperation.UNLOAD
                        && cargo.behavior().loadStation().isPresent() && !cargo.behavior().sourceInventories().isEmpty()).toList();
    }

    static List<Waypoint> destinations(@Nullable Route route, @Nullable Waypoint origin) {
        return route == null || origin == null ? List.of() : route.getWaypoints().stream()
                .filter(waypoint -> waypoint.id() != origin.id() && waypoint.dimension().equals(origin.dimension())
                        && waypoint.action() instanceof WaypointAction.Cargo cargo
                        && cargo.behavior().operation() != CargoOperation.LOAD && cargo.behavior().unloadStation().isPresent()).toList();
    }

    static int ordinal(@Nullable Route route, int waypointId) {
        if (route != null) {
            for (int index = 0; index < route.getWaypoints().size(); index++) {
                if (route.getWaypoints().get(index).id() == waypointId) return index + 1;
            }
        }
        return 0;
    }

    static Component itemName(Identifier id) {
        var item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        return item == null ? Component.literal(id.toString()) : item.getDefaultInstance().getHoverName();
    }

    static Component itemName(ItemResource resource) { return resource.getHoverName(); }
}
