package com.lastcallsoftware.farandwide.route.client;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.VehicleRouteAssignment;

import java.util.HashSet;
import java.util.Set;

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
    private static final int CONTROL_SIZE = 16;
    private static final int CONTROL_GAP = 4;
    private static final int CONTROL_RIGHT_PADDING = 4;
    private static final int WAYPOINT_TEXT_PADDING = 6;

    private RouteList routeList;
    private Button selectButton;
    private Button editButton;
    private Button deleteButton;
    private long displayedRouteStateRevision = Long.MIN_VALUE;
    private final Set<Integer> expandedRouteIds = new HashSet<>();

    public RouteManagementScreen() {
        super(Component.translatable("screen.farandwide.manage_routes.title"));
    }

    @Override
    protected void init() {
        int listHeight = Math.max(40, height - LIST_TOP - 88);
        int panelX = (width - PANEL_WIDTH) / 2;
        routeList = addRenderableWidget(new RouteList(minecraft, PANEL_WIDTH, listHeight, LIST_TOP, 24));
        routeList.updateSizeAndPosition(PANEL_WIDTH, listHeight, panelX, LIST_TOP);

        RouteManager.refreshServerSnapshot();
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
            routeList.addRow(entry);
            if (route.getId() == selectedId || (selectedId < 0 && route == currentRoute)) {
                routeList.setSelected(entry);
            }
            if (expandedRouteIds.contains(route.getId())) {
                for (VehicleRouteAssignment assignment : RouteManager.getVehicleAssignments(route.getId())) {
                    routeList.addRow(new VehicleEntry(route, assignment));
                }
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
        RouteListEntry entry = routeList.getSelected();
        return entry instanceof RouteEntry routeEntry ? routeEntry.route() : null;
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

        confirmDeleteRoute(route);
    }

    private void confirmDeleteRoute(Route route) {
        minecraft.setScreenAndShow(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        RouteManager.removeRoute(route);
                    }
                    minecraft.setScreenAndShow(new RouteManagementScreen());
                },
                Component.translatable("screen.farandwide.manage_routes.delete.message", route.getName()),
                Component.empty()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, title, (width - font.width(title)) / 2, 15, 0xFFFFFFFF);
    }

    private void drawActivationButton(GuiGraphicsExtractor graphics, int x, int y, boolean stopControl) {
        int border = stopControl ? 0xFFFF7777 : 0xFF77FF77;
        int fill = stopControl ? 0xFF9E2828 : 0xFF287A3C;
        graphics.fill(x, y, x + CONTROL_SIZE, y + CONTROL_SIZE, border);
        graphics.fill(x + 1, y + 1, x + CONTROL_SIZE - 1, y + CONTROL_SIZE - 1, fill);
        if (stopControl) {
            int centerX = x + CONTROL_SIZE / 2;
            int centerY = y + CONTROL_SIZE / 2;
            graphics.fill(centerX - 3, centerY - 3, centerX + 3, centerY + 3, 0xFFFFFFFF);
        } else {
            int left = x + CONTROL_SIZE / 2 - 2;
            int top = y + CONTROL_SIZE / 2 - 4;
            for (int row = 0; row < 8; row++) {
                int glyphWidth = row < 4 ? row + 2 : 9 - row;
                graphics.fill(left, top + row, left + glyphWidth, top + row + 1, 0xFFFFFFFF);
            }
        }
    }

    private void drawDeleteButton(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y, x + CONTROL_SIZE, y + CONTROL_SIZE, 0xFFFF8888);
        graphics.fill(x + 1, y + 1, x + CONTROL_SIZE - 1, y + CONTROL_SIZE - 1, 0xFF672828);
        int centerX = x + CONTROL_SIZE / 2;
        int centerY = y + CONTROL_SIZE / 2;
        for (int offset = -3; offset <= 3; offset++) {
            graphics.fill(centerX + offset, centerY + offset, centerX + offset + 1, centerY + offset + 1,
                    0xFFFFFFFF);
            graphics.fill(centerX + offset, centerY - offset, centerX + offset + 1, centerY - offset + 1,
                    0xFFFFFFFF);
        }
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + CONTROL_SIZE
                && mouseY >= y && mouseY < y + CONTROL_SIZE;
    }

    private class RouteList extends ObjectSelectionList<RouteListEntry> {
        RouteList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        void addRow(RouteListEntry entry) {
            addEntry(entry);
        }

        @Override
        public int getRowWidth() {
            return getWidth() - 20;
        }

        @Override
        public void setSelected(RouteListEntry entry) {
            super.setSelected(entry);
            updateButtonState();
        }
    }

    private abstract class RouteListEntry extends ObjectSelectionList.Entry<RouteListEntry> {
    }

    private class RouteEntry extends RouteListEntry {
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
            int disclosureX = getContentX() + 4;
            int iconX = disclosureX + 12;
            int iconY = getContentY() + 1;
            java.util.List<VehicleRouteAssignment> assignments = RouteManager.getVehicleAssignments(route.getId());
            boolean hasVehicles = !assignments.isEmpty();
            boolean routeActive = RouteManager.isRouteActive(route.getId());
            int deleteX = getContentX() + list.getRowWidth() - CONTROL_SIZE - CONTROL_RIGHT_PADDING;
            int activationX = deleteX - CONTROL_SIZE - CONTROL_GAP;
            int activationY = getContentY() + 3;
            Component disclosure = hasVehicles
                    ? Component.literal(expandedRouteIds.contains(route.getId()) ? "▼" : "▶")
                    : Component.literal(" ");
            graphics.text(font, disclosure, disclosureX, getContentY() + 5, 0xFFAAAAAA);
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

            if (hasVehicles) {
                drawActivationButton(graphics, activationX, activationY, routeActive);
            }
            drawDeleteButton(graphics, deleteX, activationY);

            if (hovered && inside(mouseX, mouseY, deleteX, activationY)) {
                graphics.setTooltipForNextFrame(
                        font,
                        Component.translatable("screen.farandwide.manage_routes.delete_route", route.getName()),
                        mouseX,
                        mouseY);
            } else if (hovered && hasVehicles && inside(mouseX, mouseY, activationX, activationY)) {
                graphics.setTooltipForNextFrame(
                        font,
                        Component.translatable(routeActive
                                ? "screen.farandwide.manage_routes.deactivate_route"
                                : "screen.farandwide.manage_routes.activate_route", route.getName()),
                        mouseX,
                        mouseY);
            } else if (hovered
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
            java.util.List<VehicleRouteAssignment> assignments = RouteManager.getVehicleAssignments(route.getId());
            int deleteX = getContentX() + list.getRowWidth() - CONTROL_SIZE - CONTROL_RIGHT_PADDING;
            int activationX = deleteX - CONTROL_SIZE - CONTROL_GAP;
            int activationY = getContentY() + 3;
            if (event.button() == 0 && inside(event.x(), event.y(), deleteX, activationY)) {
                confirmDeleteRoute(route);
                return true;
            }
            if (event.button() == 0 && !assignments.isEmpty()
                    && inside(event.x(), event.y(), activationX, activationY)) {
                boolean routeActive = RouteManager.isRouteActive(route.getId());
                RouteManager.setRouteAssignmentsActive(route.getId(), !routeActive);
                return true;
            }
            if (event.button() == 0 && event.x() >= getContentX()
                    && event.x() < getContentX() + 16
                    && !RouteManager.getVehicleAssignments(route.getId()).isEmpty()) {
                if (!expandedRouteIds.add(route.getId())) {
                    expandedRouteIds.remove(route.getId());
                }
                list.setSelected(this);
                refreshRouteList();
                return true;
            }
            list.setSelected(this);
            return true;
        }
    }

    private class VehicleEntry extends RouteListEntry {
        private final Route route;
        private final VehicleRouteAssignment assignment;

        VehicleEntry(Route route, VehicleRouteAssignment assignment) {
            this.route = route;
            this.assignment = assignment;
        }

        @Override
        public Component getNarration() {
            return Component.translatable(
                    "screen.farandwide.manage_routes.vehicle_narration",
                    assignment.displayName(),
                    Component.translatable(assignment.active()
                            ? "screen.farandwide.manage_routes.vehicle_active"
                            : "screen.farandwide.manage_routes.vehicle_inactive"),
                    Component.translatable(assignment.traversalDirection() > 0
                            ? "screen.farandwide.manage_routes.direction_positive"
                            : "screen.farandwide.manage_routes.direction_negative"),
                    assignment.targetWaypointIndex() + 1,
                    route.getWaypoints().size());
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                boolean hovered, float partialTick) {
            int x = getContentX();
            int y = getContentY() + 3;
            int activationXOffset = activationXOffset();
            drawActivationButton(graphics, x + activationXOffset, y, assignment.active());
            drawDeleteButton(graphics, x + deleteXOffset(), y);
            int centeredTextY = y + (CONTROL_SIZE - font.lineHeight) / 2;
            Component vehicleName = Component.literal(assignment.displayName());
            int vehicleNameX = x + 24;
            graphics.text(font, vehicleName, vehicleNameX, centeredTextY, 0xFFD0D0D0);

            boolean canDecrease = assignment.targetWaypointIndex() > 0;
            boolean canIncrease = assignment.targetWaypointIndex() + 1 < route.getWaypoints().size();
            Component waypoint = waypointLabel();
            int minusXOffset = minusXOffset();
            drawStepButton(graphics, x + minusXOffset, y, "↑", canDecrease);
            int directionXOffset = directionXOffset();
            drawStepButton(
                    graphics, x + directionXOffset, y, assignment.traversalDirection() > 0 ? "+" : "-", true);
            int plusXOffset = plusXOffset();
            int waypointX = x + minusXOffset + CONTROL_SIZE + WAYPOINT_TEXT_PADDING;
            graphics.text(font, waypoint, waypointX, centeredTextY, 0xFFFFFFFF);
            drawStepButton(graphics, x + plusXOffset, y, "↓", canIncrease);

            if (hovered) {
                Component tooltip = null;
                if (inside(mouseX, mouseY, x + directionXOffset, y)) {
                    tooltip = Component.translatable(assignment.traversalDirection() > 0
                            ? "screen.farandwide.manage_routes.reverse_negative"
                            : "screen.farandwide.manage_routes.reverse_positive",
                            assignment.displayName());
                } else if (inside(mouseX, mouseY, x + minusXOffset, y)) {
                    tooltip = Component.translatable("screen.farandwide.manage_routes.previous_waypoint");
                } else if (inside(mouseX, mouseY, x + plusXOffset, y)) {
                    tooltip = Component.translatable("screen.farandwide.manage_routes.next_waypoint");
                } else if (inside(mouseX, mouseY, x + deleteXOffset(), y)) {
                    tooltip = Component.translatable(
                            "screen.farandwide.manage_routes.unassign_vehicle", assignment.displayName());
                } else if (inside(mouseX, mouseY, x + activationXOffset, y)) {
                    tooltip = Component.translatable(assignment.active()
                            ? "screen.farandwide.manage_routes.deactivate_vehicle"
                            : "screen.farandwide.manage_routes.activate_vehicle",
                            assignment.displayName());
                } else if (mouseX >= vehicleNameX && mouseX < vehicleNameX + font.width(vehicleName)
                        && mouseY >= centeredTextY && mouseY < centeredTextY + font.lineHeight) {
                    tooltip = assignment.position()
                            .<Component>map(position -> Component.translatable(
                                    position.current()
                                            ? "screen.farandwide.manage_routes.vehicle_position"
                                            : "screen.farandwide.manage_routes.vehicle_last_known_position",
                                    position.blockPosition().getX(),
                                    position.blockPosition().getY(),
                                    position.blockPosition().getZ(),
                                    position.dimension().toString()))
                            .orElseGet(() -> Component.translatable(
                                    "screen.farandwide.manage_routes.vehicle_position_unavailable"));
                }
                if (tooltip != null) {
                    graphics.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
                }
            }
        }

        private void drawStepButton(GuiGraphicsExtractor graphics, int x, int y, String label, boolean active) {
            int border = active ? 0xFFFFFFFF : 0xFF555555;
            int fill = active ? 0xFF555555 : 0xFF222222;
            graphics.fill(x, y, x + CONTROL_SIZE, y + CONTROL_SIZE, border);
            graphics.fill(x + 1, y + 1, x + CONTROL_SIZE - 1, y + CONTROL_SIZE - 1, fill);
            Component text = Component.literal(label);
            graphics.text(font, text, x + (CONTROL_SIZE - font.width(text)) / 2, y + 3,
                    active ? 0xFFFFFFFF : 0xFF777777);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() != 0) {
                return false;
            }
            int x = getContentX();
            int y = getContentY() + 3;
            if (inside(event, x + deleteXOffset(), y)) {
                RouteManager.unassignVehicle(assignment.assigneeId());
                return true;
            }
            if (inside(event, x + activationXOffset(), y)) {
                RouteManager.setVehicleAssignmentActive(assignment.assigneeId(), !assignment.active());
                return true;
            }
            if (inside(event, x + directionXOffset(), y)) {
                RouteManager.reverseVehicleDirection(assignment.assigneeId());
                return true;
            }
            if (inside(event, x + minusXOffset(), y) && assignment.targetWaypointIndex() > 0) {
                RouteManager.moveVehicleTargetWaypoint(assignment.assigneeId(), -1);
                return true;
            }
            if (inside(event, x + plusXOffset(), y)
                    && assignment.targetWaypointIndex() + 1 < route.getWaypoints().size()) {
                RouteManager.moveVehicleTargetWaypoint(assignment.assigneeId(), 1);
                return true;
            }
            return false;
        }

        private boolean inside(MouseButtonEvent event, int x, int y) {
            return inside(event.x(), event.y(), x, y);
        }

        private boolean inside(double mouseX, double mouseY, int x, int y) {
            return RouteManagementScreen.inside(mouseX, mouseY, x, y);
        }

        private int deleteXOffset() {
            return routeList.getRowWidth() - CONTROL_SIZE - CONTROL_RIGHT_PADDING;
        }

        private int activationXOffset() {
            return deleteXOffset() - CONTROL_SIZE - CONTROL_GAP;
        }

        private int plusXOffset() {
            return directionXOffset() - CONTROL_SIZE - CONTROL_GAP;
        }

        private int minusXOffset() {
            return plusXOffset() - WAYPOINT_TEXT_PADDING * 2 - font.width(waypointLabel()) - CONTROL_SIZE;
        }

        private int directionXOffset() {
            return activationXOffset() - CONTROL_SIZE - CONTROL_GAP;
        }

        private Component waypointLabel() {
            return Component.translatable(
                    "screen.farandwide.manage_routes.waypoint_number",
                    assignment.targetWaypointIndex() + 1);
        }
    }
}
