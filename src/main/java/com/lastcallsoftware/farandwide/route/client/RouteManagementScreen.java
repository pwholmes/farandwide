package com.lastcallsoftware.farandwide.route.client;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.route.Route;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/**
 * Displays the routes known to {@link RouteManager}. The list highlight is kept
 * separate from the route selected in the manager until Select is pressed.
 */
public class RouteManagementScreen extends Screen {
    private static final int LIST_TOP = Constants.Client.ROUTE_LIST_TOP;
    private static final int PANEL_WIDTH = Constants.Client.ROUTE_PANEL_WIDTH;
    private static final int BUTTON_WIDTH = Constants.Client.ROUTE_BUTTON_WIDTH;
    private static final int BUTTON_GAP = Constants.Client.ROUTE_BUTTON_GAP;
    private static final int TRAVERSAL_ICON_SIZE = Constants.Client.ROUTE_TRAVERSAL_ICON_SIZE;

    private RouteList routeList;
    private Button selectButton;
    private Button editButton;
    private Button deleteButton;
    private long displayedRouteStateRevision = Long.MIN_VALUE;

    public RouteManagementScreen() {
        super(Component.translatable("screen.farandwide.manage_routes.title"));
    }

    @Override
    protected void init() {
        int listHeight = Math.max(40, height - LIST_TOP - 88);
        int panelX = (width - PANEL_WIDTH) / 2;
        routeList = addRenderableWidget(new RouteList(minecraft, PANEL_WIDTH, listHeight, LIST_TOP, 24));
        routeList.updateSizeAndPosition(PANEL_WIDTH, listHeight, panelX, LIST_TOP);

        refreshRouteList();

        int buttonsWidth = BUTTON_WIDTH * 4 + BUTTON_GAP * 3;
        int buttonX = panelX + (PANEL_WIDTH - buttonsWidth) / 2;
        int buttonY = height - 60;

        addRenderableWidget(Button.builder(
                Component.translatable("screen.farandwide.manage_routes.create"),
                button -> minecraft.setScreenAndShow(new RouteEditorScreen(null)))
                .bounds(buttonX, buttonY, BUTTON_WIDTH, 20)
                .build());

        editButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.farandwide.manage_routes.edit"),
                button -> editSelectedRoute())
                .bounds(buttonX + BUTTON_WIDTH + BUTTON_GAP, buttonY, BUTTON_WIDTH, 20)
                .build());

        deleteButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.farandwide.manage_routes.delete"),
                button -> confirmDeleteSelectedRoute())
                .bounds(buttonX + (BUTTON_WIDTH + BUTTON_GAP) * 2, buttonY, BUTTON_WIDTH, 20)
                .build());

        selectButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.farandwide.manage_routes.select"),
                button -> selectHighlightedRoute())
                .bounds(buttonX + (BUTTON_WIDTH + BUTTON_GAP) * 3, buttonY, BUTTON_WIDTH, 20)
                .build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                button -> onClose())
                .bounds((width - 100) / 2, height - 32, 100, 20)
                .build());

        updateButtonState();
        if (routeList.getSelected() != null) {
            setInitialFocus(routeList);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (displayedRouteStateRevision != RouteManager.getRouteStateRevision()) {
            refreshRouteList();
        }
    }

    private void refreshRouteList() {
        if (routeList == null) {
            return;
        }
        Route selectedBeforeRefresh = getSelectedRoute();
        int selectedId = selectedBeforeRefresh == null ? -1 : selectedBeforeRefresh.getId();
        Route currentRoute = RouteManager.getCurrentRoute();
        routeList.clearEntries();
        for (Route route : RouteManager.getRoutes()) {
            RouteEntry entry = new RouteEntry(route, routeList);
            routeList.addRoute(entry);
            if (route.getId() == selectedId || (selectedId < 0 && route == currentRoute)) {
                routeList.setSelected(entry);
            }
        }
        displayedRouteStateRevision = RouteManager.getRouteStateRevision();
        updateButtonState();
    }

    private void updateButtonState() {
        boolean hasSelection = routeList != null && routeList.getSelected() != null;
        if (selectButton != null) {
            selectButton.active = hasSelection;
        }
        if (editButton != null) {
            editButton.active = hasSelection;
        }
        if (deleteButton != null) {
            deleteButton.active = hasSelection;
        }
    }

    private Route getSelectedRoute() {
        RouteEntry entry = routeList.getSelected();
        return entry == null ? null : entry.route();
    }

    private void editSelectedRoute() {
        Route route = getSelectedRoute();
        if (route != null) {
            minecraft.setScreenAndShow(new RouteEditorScreen(route));
        }
    }

    private void selectHighlightedRoute() {
        Route route = getSelectedRoute();
        if (route != null) {
            if (route.getId() == RouteManager.getCurrentRouteId()) {
                RouteManager.clearSelectedRoute();
            } else {
                RouteManager.setSelectedRoute(route);
            }
        }
    }

    private void confirmDeleteSelectedRoute() {
        Route route = getSelectedRoute();
        if (route == null) {
            return;
        }

        minecraft.setScreenAndShow(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        RouteManager.removeRoute(route);
                    }
                    minecraft.setScreenAndShow(new RouteManagementScreen());
                },
                Component.translatable("screen.farandwide.manage_routes.delete.title"),
                Component.translatable("screen.farandwide.manage_routes.delete.message", route.getName())));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, title, (width - font.width(title)) / 2, 15, 0xFFFFFFFF);
    }

    private class RouteList extends ObjectSelectionList<RouteEntry> {
        RouteList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        void addRoute(RouteEntry entry) {
            addEntry(entry);
        }

        @Override
        public void setSelected(RouteEntry entry) {
            super.setSelected(entry);
            updateButtonState();
        }
    }

    private class RouteEntry extends ObjectSelectionList.Entry<RouteEntry> {
        private final Route route;
        private final RouteList list;

        RouteEntry(Route route, RouteList list) {
            this.route = route;
            this.list = list;
        }

        Route route() {
            return route;
        }

        @Override
        public Component getNarration() {
            return Component.translatable(
                    "screen.farandwide.manage_routes.route_entry",
                    route.getName(),
                    route.getTraversalType().getDisplayName());
        }

        @Override
        public void extractContent(
                GuiGraphicsExtractor graphics,
                int mouseX,
                int mouseY,
                boolean hovered,
                float partialTick) {
            Component name = route == RouteManager.getCurrentRoute()
                    ? Component.translatable("screen.farandwide.manage_routes.current", route.getName())
                    : Component.literal(route.getName());
            int color = route == RouteManager.getCurrentRoute() ? 0xFF55FF55 : 0xFFFFFFFF;
            int iconX = getContentX() + 4;
            int iconY = getContentY() + 1;
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    route.getTraversalType().getIcon(),
                    iconX,
                    iconY,
                    0,
                    0,
                    TRAVERSAL_ICON_SIZE,
                    TRAVERSAL_ICON_SIZE,
                    Constants.Client.TRAVERSAL_ICON_TEXTURE_SIZE,
                    Constants.Client.TRAVERSAL_ICON_TEXTURE_SIZE,
                    Constants.Client.TRAVERSAL_ICON_TEXTURE_SIZE,
                    Constants.Client.TRAVERSAL_ICON_TEXTURE_SIZE);
            graphics.text(font, name, iconX + TRAVERSAL_ICON_SIZE + 4, getContentY() + 5, color);

            if (hovered
                    && mouseX >= iconX && mouseX < iconX + TRAVERSAL_ICON_SIZE
                    && mouseY >= iconY && mouseY < iconY + TRAVERSAL_ICON_SIZE) {
                graphics.setTooltipForNextFrame(
                        font,
                        route.getTraversalType().getDisplayName(),
                        mouseX,
                        mouseY);
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            list.setSelected(this);
            return true;
        }
    }
}
