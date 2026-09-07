package com.lastcallsoftware.farandwide.route.client;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.route.RouteMetrics;
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

/** HUD horizontal and vertical direction and distance indicator for the current navigation assignment. */
public final class RouteNavigationHud {
    private static final boolean DEFAULT_HUD_VISIBLE = Constants.Client.DEFAULT_HUD_VISIBLE;
    private static final HudPosition HUD_POSITION = HudPosition.TOP_CENTER;
    private static final int HUD_MARGIN = Constants.Client.HUD_MARGIN;
    private static final Identifier NAVIGATION_BADGE = Identifier.fromNamespaceAndPath(
            "farandwide",
            "textures/gui/navigation_badge.png");
    private static final Identifier NAVIGATION_NEEDLE = Identifier.fromNamespaceAndPath(
            "farandwide",
            "textures/gui/navigation_needle.png");
    private static final Identifier NAVIGATION_BULLSEYE = Identifier.fromNamespaceAndPath(
            "farandwide",
            "textures/gui/navigation_bullseye.png");
    private static final int BADGE_TEXTURE_SIZE = Constants.Client.NAVIGATION_BADGE_TEXTURE_SIZE;
    private static final int NEEDLE_TEXTURE_SIZE = Constants.Client.NAVIGATION_NEEDLE_TEXTURE_SIZE;
    private static final int BULLSEYE_TEXTURE_SIZE = Constants.Client.NAVIGATION_BULLSEYE_TEXTURE_SIZE;
    private static final int INDICATOR_DISPLAY_SIZE = Constants.Client.NAVIGATION_INDICATOR_DISPLAY_SIZE;
    private static final int NEEDLE_DISPLAY_SIZE = Constants.Client.NAVIGATION_NEEDLE_DISPLAY_SIZE;
    private static final int BULLSEYE_DISPLAY_SIZE = Constants.Client.NAVIGATION_BULLSEYE_DISPLAY_SIZE;
    private static final double BULLSEYE_EXIT_MARGIN = Constants.Client.NAVIGATION_BULLSEYE_EXIT_MARGIN;
    private static final double VERTICAL_DEAD_ZONE_BLOCKS = Constants.Client.NAVIGATION_VERTICAL_DEAD_ZONE_BLOCKS;
    private static final float VERTICAL_DEAD_ZONE_DEGREES = Constants.Client.NAVIGATION_VERTICAL_DEAD_ZONE_DEGREES;
    private static final float VERTICAL_STEEP_ANGLE_DEGREES =
            Constants.Client.NAVIGATION_VERTICAL_STEEP_ANGLE_DEGREES;
    private static final int TRAVERSAL_ICON_SIZE = Constants.Client.HUD_TRAVERSAL_ICON_SIZE;
    private static final int TITLE_GAP = Constants.Client.HUD_TITLE_GAP;
    private static final int STATUS_LINE_GAP = 2;
    private static float displayedAngle;
    private static boolean hasDisplayedAngle;
    private static boolean bullseyeVisible;
    private static int bullseyeRouteId = -1;
    private static int bullseyeWaypointId = -1;
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
        resetBullseye();
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
        if (assignment != null && assignedRoute != null && target != null) {
            long targetDistance = Math.round(target.position().distanceTo(navigationEntity.position()));
            long remainingDistance = Math.round(RouteMetrics.remainingDistance(
                    assignedRoute,
                    navigationEntity.position(),
                    assignment.getTargetWaypointIndex(),
                    assignment.getTraversalDirection()));
            waypointLabel = Component.translatable(
                    "hud.farandwide.waypoint",
                    assignment.getTraversalDirection() > 0 ? "+" : "-",
                    assignment.getTargetWaypointIndex() + 1,
                    assignedRoute.getWaypoints().size(),
                    targetDistance,
                    remainingDistance);
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

        if (target == null || assignment == null) {
            hasDisplayedAngle = false;
            resetBullseye();
            return;
        }

        Vec3 delta = target.position().subtract(navigationEntity.position());
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        boolean sameBullseyeTarget = assignment.getRouteId() == bullseyeRouteId
                && target.id() == bullseyeWaypointId;
        bullseyeVisible = shouldShowBullseye(
                horizontalDistance, target.arrivalRadius(), bullseyeVisible && sameBullseyeTarget);
        bullseyeRouteId = assignment.getRouteId();
        bullseyeWaypointId = target.id();

        float targetYaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float targetAngle = Mth.wrapDegrees(targetYaw - minecraft.player.getYRot());
        if (!hasDisplayedAngle) {
            displayedAngle = targetAngle;
            hasDisplayedAngle = true;
        } else {
            displayedAngle += Mth.wrapDegrees(targetAngle - displayedAngle) * 0.2F;
        }

        if (bullseyeVisible) {
            drawBullseye(graphics, centerX, centerY);
        } else {
            drawBadge(graphics, centerX, centerY);
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
        }

        float elevationAngle = (float) Math.toDegrees(Math.atan2(delta.y, horizontalDistance));
        drawVerticalChevrons(graphics, centerX, centerY, delta.y, elevationAngle);

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
                : customName;
    }

    private static Component genericAssigneeLabel(Entity assignee) {
        String managedDisplayName = RouteManager.getManagedAssigneeDisplayName(assignee.getId());
        return managedDisplayName == null
                ? assignee.getType().getDescription()
                : Component.literal(managedDisplayName);
    }

    private static void drawBadge(GuiGraphicsExtractor graphics, int centerX, int centerY) {
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                NAVIGATION_BADGE,
                centerX - INDICATOR_DISPLAY_SIZE / 2,
                centerY - INDICATOR_DISPLAY_SIZE / 2,
                0,
                0,
                INDICATOR_DISPLAY_SIZE,
                INDICATOR_DISPLAY_SIZE,
                BADGE_TEXTURE_SIZE,
                BADGE_TEXTURE_SIZE,
                BADGE_TEXTURE_SIZE,
                BADGE_TEXTURE_SIZE);
    }

    /** Keeps the bullseye stable near the radius boundary while switching targets immediately. */
    static boolean shouldShowBullseye(double horizontalDistance, double arrivalRadius, boolean alreadyVisible) {
        double threshold = arrivalRadius + (alreadyVisible ? BULLSEYE_EXIT_MARGIN : 0.0);
        return horizontalDistance <= threshold;
    }

    private static void resetBullseye() {
        bullseyeVisible = false;
        bullseyeRouteId = -1;
        bullseyeWaypointId = -1;
    }

    /** Draws the archery target when the waypoint's horizontal position has been acquired. */
    private static void drawBullseye(GuiGraphicsExtractor graphics, int centerX, int centerY) {
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                NAVIGATION_BULLSEYE,
                centerX - BULLSEYE_DISPLAY_SIZE / 2,
                centerY - BULLSEYE_DISPLAY_SIZE / 2,
                0,
                0,
                BULLSEYE_DISPLAY_SIZE,
                BULLSEYE_DISPLAY_SIZE,
                BULLSEYE_TEXTURE_SIZE,
                BULLSEYE_TEXTURE_SIZE,
                BULLSEYE_TEXTURE_SIZE,
                BULLSEYE_TEXTURE_SIZE);
    }

    /** Draws one fixed chevron for a climb or descent, and a second for a steep elevation angle. */
    private static void drawVerticalChevrons(
            GuiGraphicsExtractor graphics, int centerX, int centerY, double verticalDifference, float elevationAngle) {
        if (Math.abs(verticalDifference) <= VERTICAL_DEAD_ZONE_BLOCKS
                || Math.abs(elevationAngle) <= VERTICAL_DEAD_ZONE_DEGREES) {
            return;
        }

        boolean pointsUp = elevationAngle > 0.0F;
        int count = Math.abs(elevationAngle) >= VERTICAL_STEEP_ANGLE_DEGREES ? 2 : 1;
        int x = centerX + INDICATOR_DISPLAY_SIZE / 2 + 2;
        int firstY = pointsUp ? centerY - count * 5 + 1 : centerY;
        for (int index = 0; index < count; index++) {
            int y = firstY + index * 5;
            drawChevron(graphics, x + 1, y + 1, pointsUp, 0xCC000000);
            drawChevron(graphics, x, y, pointsUp, 0xFFFFFFFF);
        }
    }

    /** Draws a compact seven-pixel-wide chevron without relying on font glyph coverage. */
    private static void drawChevron(GuiGraphicsExtractor graphics, int x, int y, boolean pointsUp, int color) {
        int apexY = pointsUp ? y : y + 3;
        int innerY = pointsUp ? y + 1 : y + 2;
        int outerY = pointsUp ? y + 2 : y + 1;
        int edgeY = pointsUp ? y + 3 : y;
        graphics.fill(x + 3, apexY, x + 4, apexY + 1, color);
        graphics.fill(x + 2, innerY, x + 3, innerY + 1, color);
        graphics.fill(x + 4, innerY, x + 5, innerY + 1, color);
        graphics.fill(x + 1, outerY, x + 2, outerY + 1, color);
        graphics.fill(x + 5, outerY, x + 6, outerY + 1, color);
        graphics.fill(x, edgeY, x + 1, edgeY + 1, color);
        graphics.fill(x + 6, edgeY, x + 7, edgeY + 1, color);
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
