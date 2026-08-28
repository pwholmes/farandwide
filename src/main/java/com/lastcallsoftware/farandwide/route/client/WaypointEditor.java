package com.lastcallsoftware.farandwide.route.client;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.Waypoint;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-side waypoint targeting; all resulting mutations remain server-authoritative. */
public final class WaypointEditor {
    private static int targetedWaypointId;

    private WaypointEditor() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(WaypointEditor::onClientTick);
        NeoForge.EVENT_BUS.addListener(WaypointEditor::onInteraction);
    }

    public static boolean isTargeted(Waypoint waypoint) {
        return waypoint.id() == targetedWaypointId;
    }

    public static void editTargetedWaypoint() {
        Minecraft minecraft = Minecraft.getInstance();
        Route route = RouteManager.getCurrentRoute();
        Waypoint waypoint = targetedWaypoint(route);
        if (route == null || waypoint == null) {
            showMessage("message.farandwide.no_waypoint_targeted");
        } else {
            minecraft.setScreenAndShow(new CargoWaypointScreen(route, waypoint));
        }
    }

    /** Deletes only the waypoint currently highlighted by the marker targeter. */
    public static void deleteTargetedWaypoint() {
        Route route = RouteManager.getCurrentRoute();
        Waypoint waypoint = targetedWaypoint(route);
        if (route == null || waypoint == null) {
            showMessage("message.farandwide.no_waypoint_targeted");
        } else {
            RouteManager.deleteWaypoint(route, waypoint.id());
        }
    }

    /** Deletes the highlighted marker, or adds a new waypoint when no marker is highlighted. */
    public static void toggleTargetedWaypoint(Route route) {
        Waypoint waypoint = targetedWaypoint(route);
        if (waypoint == null) {
            RouteManager.addCurrentPosition(route);
        } else {
            RouteManager.deleteWaypoint(route, waypoint.id());
        }
    }

    public static void reset() {
        targetedWaypointId = 0;
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Route route = RouteManager.getCurrentRoute();
        if (minecraft.player == null || route == null) {
            targetedWaypointId = 0;
            return;
        }
        // Preserve the aimed marker while the command menu is open so its
        // context-sensitive Edit Waypoint action can use that selection.
        if (minecraft.gui.screen() != null) {
            return;
        }
        Waypoint target = selectTarget(
                route.getWaypoints(),
                minecraft.player.level().dimension().identifier(),
                minecraft.player.getEyePosition(),
                minecraft.player.getLookAngle(),
                Constants.Waypoints.EDIT_RADIUS,
                Constants.Waypoints.TARGET_RADIUS);
        targetedWaypointId = target == null ? 0 : target.id();
    }

    private static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (CargoStationSelector.isSelecting() || !event.isUseItem() || targetedWaypointId == 0) {
            return;
        }

        // Waypoints are client-rendered markers, not blocks. Consume use only
        // when one is highlighted so the block behind it is not interacted with.
        event.setCanceled(true);
        event.setSwingHand(false);
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            editTargetedWaypoint();
        }
    }

    static Waypoint selectTarget(List<Waypoint> waypoints, Identifier dimension, Vec3 eyePosition,
            Vec3 lookDirection, double maximumDistance, double targetRadius) {
        Vec3 direction = lookDirection.normalize();
        Waypoint nearest = null;
        double nearestIntersection = Double.MAX_VALUE;
        for (Waypoint waypoint : waypoints) {
            if (!waypoint.dimension().equals(dimension)) {
                continue;
            }
            Vec3 offset = waypoint.position().add(0, Constants.Waypoints.MARKER_HEIGHT_OFFSET, 0)
                    .subtract(eyePosition);
            double projection = offset.dot(direction);
            if (projection < 0 || projection > maximumDistance) {
                continue;
            }
            double perpendicularSquared = offset.lengthSqr() - projection * projection;
            double radiusSquared = targetRadius * targetRadius;
            if (perpendicularSquared > radiusSquared) {
                continue;
            }
            double intersection = projection - Math.sqrt(Math.max(0, radiusSquared - perpendicularSquared));
            if (intersection < nearestIntersection) {
                nearestIntersection = intersection;
                nearest = waypoint;
            }
        }
        return nearest;
    }

    private static Waypoint targetedWaypoint(Route route) {
        return route == null ? null : route.getWaypoints().stream()
                .filter(candidate -> candidate.id() == targetedWaypointId)
                .findFirst().orElse(null);
    }

    private static void showMessage(String translationKey) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendOverlayMessage(Component.translatable(translationKey));
        }
    }
}
