package com.lastcallsoftware.farandwide.route;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/** HUD direction and distance indicator for the route assigned to the ridden vehicle. */
public final class RouteNavigationHud {
    private static final int ARROW_COLOR = 0xDDFFFF00;

    private RouteNavigationHud() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RouteNavigationHud::onRenderGui);
    }

    public static RouteAssignment getRiddenAssignment() {
        Minecraft minecraft = Minecraft.getInstance();
        Entity vehicle = minecraft.player == null ? null : minecraft.player.getVehicle();
        return RouteManager.getActiveAssignment(vehicle);
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        RouteAssignment assignment = getRiddenAssignment();
        Waypoint target = RouteManager.getTargetWaypoint(assignment);
        if (assignment == null || target == null) {
            return;
        }

        Entity vehicle = minecraft.player.getVehicle();
        Vec3 delta = target.position().subtract(vehicle.position());
        double distance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float targetYaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float relativeAngle = (float) Math.toRadians(targetYaw - minecraft.player.getYRot());

        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2 + 28;

        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().rotate(relativeAngle);
        graphics.fill(-2, -10, 3, 9, ARROW_COLOR);
        graphics.pose().pushMatrix();
        graphics.pose().translate(0, -10);
        graphics.pose().rotate((float) (-Math.PI / 4.0));
        graphics.fill(-2, 0, 3, 10, ARROW_COLOR);
        graphics.pose().popMatrix();
        graphics.pose().pushMatrix();
        graphics.pose().translate(0, -10);
        graphics.pose().rotate((float) (Math.PI / 4.0));
        graphics.fill(-2, 0, 3, 10, ARROW_COLOR);
        graphics.pose().popMatrix();
        graphics.pose().popMatrix();

        Component label = Component.translatable(
                "hud.farandwide.waypoint",
                assignment.getTargetWaypointIndex() + 1,
                Math.round(distance));
        graphics.centeredText(minecraft.font, label, centerX, centerY + 13, 0xFFFFFFFF);
    }
}
