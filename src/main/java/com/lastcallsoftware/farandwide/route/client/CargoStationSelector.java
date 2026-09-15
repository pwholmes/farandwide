package com.lastcallsoftware.farandwide.route.client;

import com.lastcallsoftware.farandwide.route.CargoStationBinding;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Temporarily captures one client-targeted block for the cargo waypoint editor. */
final class CargoStationSelector {
    private static PendingSelection pending;

    private CargoStationSelector() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(CargoStationSelector::onInteraction);
        NeoForge.EVENT_BUS.addListener(CargoStationSelector::onKeyInput);
        NeoForge.EVENT_BUS.addListener(CargoStationSelector::onClientTick);
        NeoForge.EVENT_BUS.addListener(CargoStationSelector::onRenderGui);
    }

    static void begin(CargoWaypointScreen screen, Role role) {
        pending = new PendingSelection(screen, role);
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreenAndShow(null);
    }

    static void reset() {
        pending = null;
    }

    /** Discards the hidden waypoint editor when another action removes its waypoint. */
    static void cancel() {
        pending = null;
    }

    static boolean isSelecting() {
        return pending != null;
    }

    private static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        PendingSelection selection = pending;
        if (selection == null || !event.isUseItem()) {
            return;
        }
        // Use-item is reported once for each hand. Consume both reports so the
        // selected inventory never opens as a side effect of choosing it.
        event.setCanceled(true);
        event.setSwingHand(false);
        if (selection.complete || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.hitResult instanceof BlockHitResult hit) {
            CargoStationBinding station = new CargoStationBinding(hit.getBlockPos(), hit.getDirection());
            if (!(selection.role == Role.SOURCE ? selection.screen.isSourceWithinRange(station)
                    : selection.screen.isStationWithinArrivalRadius(station))) {
                if (minecraft.player != null) {
                    minecraft.player.sendOverlayMessage(
                            selection.role == Role.SOURCE
                                    ? Component.translatable("message.farandwide.source_out_of_range", (int) com.lastcallsoftware.farandwide.Constants.Orders.SOURCE_RADIUS)
                                    : Component.translatable("message.farandwide.cargo_station_out_of_range"));
                }
                return;
            }
            if (selection.role == Role.SOURCE && !selection.screen.isInventory(station)) {
                if (minecraft.player != null) {
                    minecraft.player.sendOverlayMessage(Component.translatable("message.farandwide.source_not_inventory"));
                }
                return;
            }
            selection.complete = true;
            selection.screen.setSelectedStation(selection.role, station);
        } else {
            selection.complete = true;
            selection.screen.stationSelectionCancelled();
        }
        minecraft.setScreenAndShow(selection.screen);
    }

    private static void onKeyInput(InputEvent.Key event) {
        PendingSelection selection = pending;
        if (selection == null
                || selection.complete
                || event.getKey() != InputConstants.KEY_ESCAPE
                || event.getAction() != InputConstants.PRESS) {
            return;
        }

        selection.complete = true;
        selection.screen.stationSelectionCancelled();
        Minecraft.getInstance().setScreenAndShow(selection.screen);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (pending != null && (pending.complete || Minecraft.getInstance().player == null)) {
            pending = null;
        }
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        if (pending == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        graphics.centeredText(
                minecraft.font,
                net.minecraft.network.chat.Component.translatable("message.farandwide.cargo_station_selecting"),
                graphics.guiWidth() / 2,
                // Leave the action-bar line free for selection errors and other feedback.
                graphics.guiHeight() - 84,
                0xFFFFFFFF);
    }

    private static final class PendingSelection {
        private final CargoWaypointScreen screen;
        private final Role role;
        private boolean complete;

        PendingSelection(CargoWaypointScreen screen, Role role) {
            this.screen = screen;
            this.role = role;
        }
    }

    enum Role {
        LOAD,
        UNLOAD,
        SOURCE
    }
}
