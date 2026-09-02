package com.lastcallsoftware.farandwide.route.client;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.route.TraversalType;
import com.lastcallsoftware.farandwide.route.Waypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/** HUD direction and distance indicator for the player's current navigation assignment. */
public final class RouteNavigationHud {
    private static final boolean DEFAULT_HUD_VISIBLE = Constants.Client.DEFAULT_HUD_VISIBLE;
    private static final HudPosition HUD_POSITION = HudPosition.TOP_CENTER;
    private static final int HUD_MARGIN = Constants.Client.HUD_MARGIN;
    private static final Identifier NAVIGATION_NEEDLE = Identifier.fromNamespaceAndPath(
            "farandwide",
            "textures/gui/navigation_needle.png");
    private static final int NEEDLE_TEXTURE_SIZE = Constants.Client.NAVIGATION_NEEDLE_TEXTURE_SIZE;
    private static final int INDICATOR_DISPLAY_SIZE = Constants.Client.NAVIGATION_INDICATOR_DISPLAY_SIZE;
    private static final int NEEDLE_DISPLAY_SIZE = Constants.Client.NAVIGATION_NEEDLE_DISPLAY_SIZE;
    private static final int TRAVERSAL_ICON_SIZE = Constants.Client.HUD_TRAVERSAL_ICON_SIZE;
    private static final int TITLE_GAP = Constants.Client.HUD_TITLE_GAP;
    private static final int STATUS_LINE_GAP = 2;
    private static float displayedAngle;
    private static boolean hasDisplayedAngle;
    private static boolean visible = DEFAULT_HUD_VISIBLE;

    private RouteNavigationHud() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RouteNavigationHud::onRenderGui);
    }

    public static boolean isVisible() {
        return visible;
    }

    public static void setVisible(boolean visible) {
        RouteNavigationHud.visible = visible;
        hasDisplayedAngle = false;
    }

    public static void toggleVisibility() {
        setVisible(!visible);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.sendOverlayMessage(Component.translatable(
                    visible
                            ? "message.farandwide.hud_visible"
                            : "message.farandwide.hud_hidden"));
        }
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!visible || minecraft.player == null) {
            return;
        }

        Entity navigationEntity = minecraft.player.getVehicle() == null
                ? minecraft.player
                : minecraft.player.getVehicle();
        Route selectedRoute = RouteManager.getCurrentRoute();
        RouteAssignment assignment = RouteManager.getNavigationAssignment();
        Route assignedRoute = assignment == null ? null : RouteManager.getRoute(assignment.getRouteId());
        if (selectedRoute == null && assignedRoute == null) {
            return;
        }

        Component selectedRouteLabel = Component.translatable(
                "hud.farandwide.selected_route",
                selectedRoute == null
                        ? Component.translatable("hud.farandwide.none")
                        : Component.literal(selectedRoute.getName()));
        Component assignmentLabel = assignedRoute == null
                ? null
                : Component.translatable(
                        "hud.farandwide.assignment",
                        assigneeLabel(minecraft, navigationEntity),
                        Component.literal(assignedRoute.getName()));
        Waypoint target = RouteManager.getTargetWaypoint(assignment);

        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        int selectedRouteWidth = minecraft.font.width(selectedRouteLabel)
                + (selectedRoute == null ? 0 : TRAVERSAL_ICON_SIZE + TITLE_GAP);
        int assignmentWidth = assignmentLabel == null
                ? 0
                : minecraft.font.width(assignmentLabel) + TRAVERSAL_ICON_SIZE + TITLE_GAP;
        int contentWidth = assignmentLabel == null
                ? selectedRouteWidth
                : Math.max(selectedRouteWidth, assignmentWidth);
        Component waypointLabel = null;
        if (assignment != null && target != null) {
            waypointLabel = Component.translatable(
                    "hud.farandwide.waypoint",
                    assignment.getTraversalDirection() > 0 ? "+" : "-",
                    assignment.getTargetWaypointIndex() + 1,
                    Math.round(horizontalDistance(target.position(), navigationEntity.position())));
            contentWidth = Math.max(INDICATOR_DISPLAY_SIZE, Math.max(contentWidth, minecraft.font.width(waypointLabel)));
        }
        int centerX = HUD_POSITION.centerX(graphics.guiWidth(), contentWidth);
        int centerY = HUD_POSITION.centerY(graphics.guiHeight(), minecraft.font.lineHeight);
        drawStatus(
                graphics,
                minecraft,
                selectedRoute,
                assignedRoute,
                assignment,
                selectedRouteLabel,
                assignmentLabel,
                centerX,
                centerY);

        if (target == null) {
            hasDisplayedAngle = false;
            return;
        }

        Vec3 delta = target.position().subtract(navigationEntity.position());
        float targetYaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float targetAngle = Mth.wrapDegrees(targetYaw - minecraft.player.getYRot());
        if (!hasDisplayedAngle) {
            displayedAngle = targetAngle;
            hasDisplayedAngle = true;
        } else {
            displayedAngle += Mth.wrapDegrees(targetAngle - displayedAngle) * 0.2F;
        }

        drawBackplate(graphics, centerX, centerY);

        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().rotate((float) Math.toRadians(displayedAngle));
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                NAVIGATION_NEEDLE,
                -NEEDLE_DISPLAY_SIZE / 2,
                -NEEDLE_DISPLAY_SIZE / 2,
                0,
                0,
                NEEDLE_DISPLAY_SIZE,
                NEEDLE_DISPLAY_SIZE,
                NEEDLE_TEXTURE_SIZE,
                NEEDLE_TEXTURE_SIZE,
                NEEDLE_TEXTURE_SIZE,
                NEEDLE_TEXTURE_SIZE);
        graphics.pose().popMatrix();

        graphics.centeredText(minecraft.font, waypointLabel, centerX, centerY + 12, 0xFFFFFFFF);
    }

    private static void drawStatus(GuiGraphicsExtractor graphics, Minecraft minecraft, Route selectedRoute,
            Route assignedRoute, RouteAssignment assignment, Component selectedRouteLabel,
            Component assignmentLabel, int centerX, int centerY) {
        int statusHeight = minecraft.font.lineHeight * 2 + STATUS_LINE_GAP;
        int selectedRouteY = centerY - INDICATOR_DISPLAY_SIZE / 2 - statusHeight - 3;
        if (selectedRoute == null) {
            graphics.centeredText(minecraft.font, selectedRouteLabel, centerX, selectedRouteY, 0xFFFFFFFF);
        } else {
            drawRouteLine(
                    graphics,
                    minecraft,
                    selectedRouteLabel,
                    selectedRoute.getTraversalType(),
                    centerX,
                    selectedRouteY);
        }
        if (assignmentLabel != null && assignedRoute != null && assignment != null) {
            drawRouteLine(
                    graphics,
                    minecraft,
                    assignmentLabel,
                    assignment.getTraversalType(assignedRoute),
                    centerX,
                    selectedRouteY + minecraft.font.lineHeight + STATUS_LINE_GAP);
        }
    }

    /** Draws a route label followed by the traversal icon that applies to it. */
    private static void drawRouteLine(GuiGraphicsExtractor graphics, Minecraft minecraft, Component label,
            TraversalType traversalType, int centerX, int y) {
        int labelWidth = minecraft.font.width(label);
        int lineWidth = labelWidth + TITLE_GAP + TRAVERSAL_ICON_SIZE;
        int lineX = centerX - lineWidth / 2;
        graphics.text(minecraft.font, label, lineX, y, 0xFFFFFFFF);
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                traversalType.getIcon(),
                lineX + labelWidth + TITLE_GAP,
                y - 1,
                0,
                0,
                TRAVERSAL_ICON_SIZE,
                TRAVERSAL_ICON_SIZE,
                Constants.Client.TRAVERSAL_ICON_TEXTURE_SIZE,
                Constants.Client.TRAVERSAL_ICON_TEXTURE_SIZE,
                Constants.Client.TRAVERSAL_ICON_TEXTURE_SIZE,
                Constants.Client.TRAVERSAL_ICON_TEXTURE_SIZE);
    }

    private static Component assigneeLabel(Minecraft minecraft, Entity assignee) {
        if (assignee == minecraft.player) {
            return Component.translatable("hud.farandwide.player");
        }
        Component customName = assignee.getCustomName();
        return customName == null
                ? genericAssigneeLabel(assignee)
                : Component.translatable(
                        "hud.farandwide.named_vehicle", assignee.getType().getDescription(), customName);
    }

    private static Component genericAssigneeLabel(Entity assignee) {
        String managedDisplayName = RouteManager.getManagedAssigneeDisplayName(assignee.getId());
        return managedDisplayName == null
                ? assignee.getType().getDescription()
                : Component.literal(managedDisplayName);
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        double x = first.x - second.x;
        double z = first.z - second.z;
        return Math.sqrt(x * x + z * z);
    }

    private static void drawBackplate(GuiGraphicsExtractor graphics, int centerX, int centerY) {
        int border = 0xCC151515;
        int fill = 0x991F2523;

        graphics.fill(centerX - 5, centerY - 8, centerX + 6, centerY + 8, border);
        graphics.fill(centerX - 7, centerY - 6, centerX + 8, centerY + 7, border);
        graphics.fill(centerX - 8, centerY - 5, centerX + 8, centerY + 5, border);

        graphics.fill(centerX - 4, centerY - 7, centerX + 5, centerY + 7, fill);
        graphics.fill(centerX - 6, centerY - 5, centerX + 7, centerY + 6, fill);
        graphics.fill(centerX - 7, centerY - 4, centerX + 7, centerY + 4, fill);
    }

    private enum HudPosition {
        TOP_LEFT(Horizontal.LEFT, Vertical.TOP),
        TOP_CENTER(Horizontal.CENTER, Vertical.TOP),
        TOP_RIGHT(Horizontal.RIGHT, Vertical.TOP),
        CENTER_LEFT(Horizontal.LEFT, Vertical.CENTER),
        CENTER(Horizontal.CENTER, Vertical.CENTER),
        CENTER_RIGHT(Horizontal.RIGHT, Vertical.CENTER),
        BOTTOM_LEFT(Horizontal.LEFT, Vertical.BOTTOM),
        BOTTOM_CENTER(Horizontal.CENTER, Vertical.BOTTOM),
        BOTTOM_RIGHT(Horizontal.RIGHT, Vertical.BOTTOM);

        private final Horizontal horizontal;
        private final Vertical vertical;

        HudPosition(Horizontal horizontal, Vertical vertical) {
            this.horizontal = horizontal;
            this.vertical = vertical;
        }

        int centerX(int screenWidth, int contentWidth) {
            return switch (horizontal) {
                case LEFT -> HUD_MARGIN + contentWidth / 2;
                case CENTER -> screenWidth / 2;
                case RIGHT -> screenWidth - HUD_MARGIN - contentWidth / 2;
            };
        }

        int centerY(int screenHeight, int fontHeight) {
            return switch (vertical) {
                case TOP -> HUD_MARGIN + fontHeight * 2 + STATUS_LINE_GAP + 3 + INDICATOR_DISPLAY_SIZE / 2;
                case CENTER -> screenHeight / 2;
                case BOTTOM -> screenHeight - HUD_MARGIN - INDICATOR_DISPLAY_SIZE / 2 - 13;
            };
        }
    }

    private enum Horizontal {
        LEFT,
        CENTER,
        RIGHT
    }

    private enum Vertical {
        TOP,
        CENTER,
        BOTTOM
    }
}
