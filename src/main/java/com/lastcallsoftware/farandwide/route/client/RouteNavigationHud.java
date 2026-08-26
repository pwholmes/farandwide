package com.lastcallsoftware.farandwide.route.client;

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
    private static final boolean DEFAULT_HUD_VISIBLE = true;
    private static final HudPosition HUD_POSITION = HudPosition.TOP_CENTER;
    private static final int HUD_MARGIN = 8;
    private static final Identifier NAVIGATION_NEEDLE = Identifier.fromNamespaceAndPath(
            "farandwide",
            "textures/gui/navigation_needle.png");
    private static final int NEEDLE_TEXTURE_SIZE = 1254;
    private static final int INDICATOR_DISPLAY_SIZE = 16;
    private static final int NEEDLE_DISPLAY_SIZE = 16;
    private static final int TRAVERSAL_ICON_SIZE = 12;
    private static final int TITLE_GAP = 3;
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

        Route selectedRoute = RouteManager.getCurrentRoute();
        if (selectedRoute == null) {
            return;
        }

        RouteAssignment assignment = RouteManager.getNavigationAssignment();
        if (assignment != null && !assignment.isActive()) {
            assignment = null;
        }
        Waypoint target = RouteManager.getTargetWaypoint(assignment);
        if (assignment == null || target == null) {
            hasDisplayedAngle = false;
            drawRouteNameOnly(event.getGuiGraphics(), minecraft, selectedRoute);
            return;
        }

        Entity navigationEntity = minecraft.player.getVehicle() == null
                ? minecraft.player
                : minecraft.player.getVehicle();
        Vec3 delta = target.position().subtract(navigationEntity.position());
        double distance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float targetYaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float targetAngle = Mth.wrapDegrees(targetYaw - minecraft.player.getYRot());
        if (!hasDisplayedAngle) {
            displayedAngle = targetAngle;
            hasDisplayedAngle = true;
        } else {
            displayedAngle += Mth.wrapDegrees(targetAngle - displayedAngle) * 0.2F;
        }

        Component label = Component.translatable(
                "hud.farandwide.waypoint",
                assignment.getTargetWaypointIndex() + 1,
                Math.round(distance));
        Component routeName = Component.literal(selectedRoute.getName());
        TraversalType traversalType = assignment.getTraversalType(selectedRoute);

        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        int titleWidth = TRAVERSAL_ICON_SIZE + TITLE_GAP + minecraft.font.width(routeName);
        int contentWidth = Math.max(
                INDICATOR_DISPLAY_SIZE,
                Math.max(titleWidth, minecraft.font.width(label)));
        int centerX = HUD_POSITION.centerX(graphics.guiWidth(), contentWidth);
        int centerY = HUD_POSITION.centerY(graphics.guiHeight(), minecraft.font.lineHeight);

        int titleX = centerX - titleWidth / 2;
        int titleY = centerY - INDICATOR_DISPLAY_SIZE / 2 - minecraft.font.lineHeight - 3;
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                traversalType.getIcon(),
                titleX,
                titleY - 1,
                0,
                0,
                TRAVERSAL_ICON_SIZE,
                TRAVERSAL_ICON_SIZE,
                TraversalType.ICON_TEXTURE_SIZE,
                TraversalType.ICON_TEXTURE_SIZE,
                TraversalType.ICON_TEXTURE_SIZE,
                TraversalType.ICON_TEXTURE_SIZE);
        graphics.text(
                minecraft.font,
                routeName,
                titleX + TRAVERSAL_ICON_SIZE + TITLE_GAP,
                titleY,
                0xFFFFFFFF);
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

        graphics.centeredText(minecraft.font, label, centerX, centerY + 12, 0xFFFFFFFF);
    }

    /** Draws the selected route title when there is no active navigation target. */
    private static void drawRouteNameOnly(GuiGraphicsExtractor graphics, Minecraft minecraft, Route route) {
        Component routeName = Component.literal(route.getName());
        int titleWidth = TRAVERSAL_ICON_SIZE + TITLE_GAP + minecraft.font.width(routeName);
        int centerX = HUD_POSITION.centerX(graphics.guiWidth(), titleWidth);
        int centerY = HUD_POSITION.centerY(graphics.guiHeight(), minecraft.font.lineHeight);
        int titleY = centerY - INDICATOR_DISPLAY_SIZE / 2 - minecraft.font.lineHeight - 3;
        int titleX = centerX - titleWidth / 2;
        TraversalType traversalType = route.getTraversalType();
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                traversalType.getIcon(),
                titleX,
                titleY - 1,
                0,
                0,
                TRAVERSAL_ICON_SIZE,
                TRAVERSAL_ICON_SIZE,
                TraversalType.ICON_TEXTURE_SIZE,
                TraversalType.ICON_TEXTURE_SIZE,
                TraversalType.ICON_TEXTURE_SIZE,
                TraversalType.ICON_TEXTURE_SIZE);
        graphics.text(minecraft.font, routeName, titleX + TRAVERSAL_ICON_SIZE + TITLE_GAP, titleY, 0xFFFFFFFF);
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
                case TOP -> HUD_MARGIN + fontHeight + 3 + INDICATOR_DISPLAY_SIZE / 2;
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
