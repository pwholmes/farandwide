package com.lastcallsoftware.farandwide.client;

import java.util.Locale;

import com.lastcallsoftware.farandwide.Constants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client HUD element that displays the player's current world coordinates. */
public final class PlayerPositionHud {
    private static boolean visible;

    private PlayerPositionHud() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PlayerPositionHud::onRenderGui);
    }

    public static void toggleVisibility() {
        visible = !visible;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.sendOverlayMessage(Component.translatable(
                    visible
                            ? "message.farandwide.coordinates_visible"
                            : "message.farandwide.coordinates_hidden"));
        }
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!visible || minecraft.player == null) {
            return;
        }

        Component coordinates = Component.translatable(
                "hud.farandwide.player_position",
                formatCoordinate(minecraft.player.getX()),
                formatCoordinate(minecraft.player.getY()),
                formatCoordinate(minecraft.player.getZ()));
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        graphics.text(
                minecraft.font,
                coordinates,
                Constants.Client.HUD_MARGIN,
                graphics.guiHeight() - minecraft.font.lineHeight - Constants.Client.HUD_MARGIN,
                0xFFFFFFFF);
    }

    private static String formatCoordinate(double coordinate) {
        return String.format(Locale.ROOT, "%.1f", coordinate);
    }
}
