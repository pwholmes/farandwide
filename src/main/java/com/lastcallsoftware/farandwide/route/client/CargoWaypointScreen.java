package com.lastcallsoftware.farandwide.route.client;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.route.CargoBehavior;
import com.lastcallsoftware.farandwide.route.CargoFilter;
import com.lastcallsoftware.farandwide.route.CargoOperation;
import com.lastcallsoftware.farandwide.route.CargoStationBinding;
import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.Waypoint;
import com.lastcallsoftware.farandwide.route.WaypointAction;
import com.lastcallsoftware.farandwide.route.WaypointProximity;
import java.util.ArrayList;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;

/** Reusable create/edit screen for normal and cargo waypoint behavior. */
public final class CargoWaypointScreen extends Screen {
    private static final int CONTROL_WIDTH = Constants.Client.CARGO_WAYPOINT_CONTROL_WIDTH;
    private static final int FILTER_ITEM_SLOT_SIZE = 22;
    private static final int FILTER_ITEM_SIZE = 16;

    private final Route route;
    private final Waypoint existingWaypoint;
    private final Vec3 proposedPosition;
    private final Identifier proposedDimension;
    private CycleButton<CargoOperation> operationButton;
    private Button selectLoadStationButton;
    private Button selectUnloadStationButton;
    private Button editLoadFilterButton;
    private Button editUnloadFilterButton;
    private Button moveUpButton;
    private Button moveDownButton;
    private Button radiusDecreaseButton;
    private Button radiusIncreaseButton;
    private BehaviorType selectedBehavior;
    private CargoOperation selectedOperation;
    private CargoFilter loadFilter;
    private CargoFilter unloadFilter;
    private Optional<CargoStationBinding> selectedLoadStation;
    private Optional<CargoStationBinding> selectedUnloadStation;
    private int targetPosition;
    private double selectedArrivalRadius;
    private Component validationError;
    private int loadFilterScroll;
    private int unloadFilterScroll;
    /** Layout anchors calculated while placing the widgets and reused by the content renderer. */
    private int editorLeft;
    private int editorTop;

    /** Opens creation mode. No waypoint exists until the player presses Save. */
    public CargoWaypointScreen(Route route, Vec3 proposedPosition, Identifier proposedDimension) {
        this(route, null, proposedPosition, proposedDimension);
    }

    /** Opens edit mode with the current waypoint values. */
    public CargoWaypointScreen(Route route, Waypoint waypoint) {
        this(route, waypoint, waypoint.position(), waypoint.dimension());
    }

    private CargoWaypointScreen(Route route, Waypoint waypoint, Vec3 position, Identifier dimension) {
        super(Component.translatable(waypoint == null
                ? "screen.farandwide.cargo_waypoint.create_title"
                : "screen.farandwide.cargo_waypoint.edit_title"));
        this.route = route;
        this.existingWaypoint = waypoint;
        this.proposedPosition = position;
        this.proposedDimension = dimension;
        CargoBehavior initialCargo = waypoint != null && waypoint.action() instanceof WaypointAction.Cargo cargo
                ? cargo.behavior()
                : CargoBehavior.unfiltered(CargoOperation.LOAD);
        selectedBehavior = waypoint != null && waypoint.action() instanceof WaypointAction.Normal
                ? BehaviorType.NORMAL
                : BehaviorType.CARGO;
        selectedOperation = initialCargo.operation();
        loadFilter = initialCargo.loadFilter();
        unloadFilter = initialCargo.unloadFilter();
        selectedLoadStation = initialCargo.loadStation();
        selectedUnloadStation = initialCargo.unloadStation();
        targetPosition = waypoint == null ? -1 : route.getWaypoints().indexOf(waypoint);
        selectedArrivalRadius = waypoint == null
                ? Constants.Waypoints.DEFAULT_ARRIVAL_RADIUS
                : waypoint.arrivalRadius();
    }

    @Override
    protected void init() {
        super.init();
        renderWaypointEditorWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        renderWaypointEditorContents(graphics, mouseX, mouseY);
    }

    private static final int ROW_HEIGHT = 23;
    private int waypointControlsYPos;
    private int unloadStationYPos;
    private int unloadFilterYPos;
    private int loadFilterYPos;
    private int loadStationYPos;

    /* Render Waypoint Editor Widgets */
    private void renderWaypointEditorWidgets() {
        int left = (width - CONTROL_WIDTH) / 2;
        int top = height / 2 - 121 + font.lineHeight;
        int yPos = top + 8;
        editorLeft = left;
        editorTop = top;

        // Behavior (Normal vs Cargo)
        addRenderableWidget(CycleButton
                .builder((CargoWaypointScreen.@NonNull BehaviorType behavior) -> behavior.displayName(), selectedBehavior)
                .withValues(BehaviorType.values())
                .create(left, yPos, CONTROL_WIDTH, 20,
                        Component.translatable("screen.farandwide.cargo_waypoint.behavior"),
                        (button, value) -> {
                            selectedBehavior = value;
                            validationError = null;
                            updateCargoControls();
                        }));
        yPos += ROW_HEIGHT;

        // Waypoint (Ordinal) Position and Radius
        waypointControlsYPos = yPos;
        if (existingWaypoint != null) {
            moveUpButton = addRenderableWidget(Button.builder(Component.literal("↑"), button -> moveWaypoint(-1))
                    .bounds(left, yPos, 20, 20)
                    .build());
            moveDownButton = addRenderableWidget(Button.builder(Component.literal("↓"), button -> moveWaypoint(1))
                    .bounds(left + 100, yPos, 20, 20)
                    .build());
            radiusDecreaseButton = addRenderableWidget(Button.builder(Component.literal("−"), button -> adjustRadius(-1))
                    .bounds(left + 124, yPos, 20, 20)
                    .build());
            radiusIncreaseButton = addRenderableWidget(Button.builder(Component.literal("+"), button -> adjustRadius(1))
                    .bounds(left + CONTROL_WIDTH - 20, yPos, 20, 20)
                    .build());
            yPos += ROW_HEIGHT;
        }

        // Cargo operation (Load, Unload, Unload then Load)
        operationButton = addRenderableWidget(CycleButton
                .builder(CargoWaypointScreen::operationName, selectedOperation)
                .withValues(CargoOperation.values())
                .create(left, yPos, CONTROL_WIDTH, 20,
                        Component.translatable("screen.farandwide.cargo_waypoint.operation"),
                        (button, value) -> {
                            selectedOperation = value;
                            validationError = null;
                            // The operation changes how many transfer sections exist, so rebuild the
                            // screen rather than leaving the later sections at coordinates for the old operation.
                            clearWidgets();
                            renderWaypointEditorWidgets();
                        }));
        yPos += ROW_HEIGHT;

        // Unload buttons
        selectLoadStationButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.farandwide.cargo_waypoint.select_load_station"),
                button -> CargoStationSelector.begin(this, CargoStationSelector.Role.LOAD))
                .bounds(left, yPos, 116, 20)
                .build());
        selectUnloadStationButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.farandwide.cargo_waypoint.select_unload_station"),
                button -> CargoStationSelector.begin(this, CargoStationSelector.Role.UNLOAD))
                .bounds(left, yPos, 116, 20)
                .build());

        // Load buttons
        editLoadFilterButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.farandwide.cargo_waypoint.edit_load_filter"),
                button -> minecraft.setScreenAndShow(new CargoFilterScreen(this, true, loadFilter)))
                .bounds(left + 124, yPos, 116, 20)
                .build());
        editUnloadFilterButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.farandwide.cargo_waypoint.edit_unload_filter"),
                button -> minecraft.setScreenAndShow(new CargoFilterScreen(this, false, unloadFilter)))
                .bounds(left + 124, yPos, 116, 20)
                .build());

        if (selectedOperation == CargoOperation.UNLOAD_THEN_LOAD || selectedOperation == CargoOperation.UNLOAD) {
            unloadStationYPos = yPos;
            yPos += ROW_HEIGHT;
            unloadFilterYPos = yPos;
            yPos += (ROW_HEIGHT + 14);
        }

        if (selectedOperation == CargoOperation.UNLOAD_THEN_LOAD || selectedOperation == CargoOperation.LOAD) {
            loadStationYPos = yPos;
            yPos += ROW_HEIGHT;
            loadFilterYPos = yPos;
            yPos += (ROW_HEIGHT + 14);
        }

        // Save and Cancel buttons.
        addRenderableWidget(Button.builder(
                Component.translatable("screen.farandwide.cargo_waypoint.save"),
                button -> save())
                .bounds(left, yPos, 116, 20)
                .build());
        addRenderableWidget(Button.builder(
                Component.translatable("screen.farandwide.cargo_waypoint.cancel"),
                button -> onClose())
                .bounds(left + 124, yPos, 116, 20)
                .build());
        updateCargoControls();
    }

    /**
     * Renders the non-widget portion of the waypoint editor: its title, waypoint
     * metadata, station bindings, and the read-only item-filter strips. Buttons
     * and cycle controls render themselves through {@link #extractRenderState}.
     */
    private void renderWaypointEditorContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = editorLeft;
        int top = editorTop;

        graphics.text(font, title, (width - font.width(title)) / 2, top - font.lineHeight + 2, 0xFFFFFFFF);

        if (existingWaypoint != null) {
            Component position = Component.translatable(
                    "screen.farandwide.cargo_waypoint.position", targetPosition + 1, route.getWaypoints().size());
            // These captions identify the otherwise symbolic waypoint controls, so they share the
            // anchor assigned to that row while widgets are created.
            graphics.centeredText(font, position, left + 60, waypointControlsYPos + 6, 0xFFFFFFFF);
            graphics.centeredText(font, Component.literal("Radius: %.1f".formatted(selectedArrivalRadius)),
                    left + 182, waypointControlsYPos + 6, 0xFFFFFFFF);
        }

        if (selectedBehavior == BehaviorType.CARGO) {
            if (validationError != null) {
                // Report an invalid cargo configuration in the space reserved for its first detail row;
                // this keeps the message attached to the controls that need correction.
                int validationYPos = usesUnloadStation(selectedOperation) ? unloadFilterYPos : loadFilterYPos;
                for (FormattedCharSequence line : font.split(validationError, CONTROL_WIDTH)) {
                    graphics.text(font, line, (width - font.width(line)) / 2, validationYPos, 0xFFFF5555);
                    validationYPos += font.lineHeight;
                }
            } else {
                // Cargo behavior executes its unload step before its load step, so its summary follows
                // that same order and keeps each station together with its own filter.
                if (usesUnloadStation(selectedOperation)) {
                    renderUnloadDetails(graphics, left, unloadFilterYPos, mouseX, mouseY);
                }
                if (usesLoadStation(selectedOperation)) {
                    renderLoadDetails(graphics, left, loadFilterYPos, mouseX, mouseY);
                }
            }
        }
    }

    private void renderLoadDetails(GuiGraphicsExtractor graphics, int left, int y, int mouseX, int mouseY) {
        graphics.text(font, stationDescription(selectedLoadStation, "load"), left, y, 0xFFAAAAAA);
        Component label = filterLabel(true);
        graphics.text(font, label, left, y + 14, 0xFFAAAAAA);
        if (loadFilter.isAll()) {
            graphics.text(font, Component.translatable("screen.farandwide.cargo_filter.summary_all"),
                    filterStripX(left, label), y + 14, 0xFFAAAAAA);
        } else {
            loadFilterScroll = extractFilterItemStrip(graphics, loadFilter, filterStripX(left, label), y + 14,
                    filterStripWidth(label), loadFilterScroll, mouseX, mouseY);
        }
    }

    private void renderUnloadDetails(GuiGraphicsExtractor graphics, int left, int y, int mouseX, int mouseY) {
        graphics.text(font, stationDescription(selectedUnloadStation, "unload"), left, y, 0xFFAAAAAA);
        Component label = filterLabel(false);
        graphics.text(font, label, left, y + 14, 0xFFAAAAAA);
        if (unloadFilter.isAll()) {
            graphics.text(font, Component.translatable("screen.farandwide.cargo_filter.summary_all"),
                    filterStripX(left, label), y + 14, 0xFFAAAAAA);
        } else {
            unloadFilterScroll = extractFilterItemStrip(graphics, unloadFilter, filterStripX(left, label), y + 14,
                    filterStripWidth(label), unloadFilterScroll, mouseX, mouseY);
        }
    }

    private void moveWaypoint(int amount) {
        targetPosition = Math.clamp(targetPosition + amount, 0, route.getWaypoints().size() - 1);
        if (moveUpButton != null) {
            moveUpButton.active = targetPosition > 0;
        }
        if (moveDownButton != null) {
            moveDownButton.active = targetPosition < route.getWaypoints().size() - 1;
        }
    }

    private void adjustRadius(int steps) {
        selectedArrivalRadius = Math.clamp(selectedArrivalRadius + steps * Constants.Waypoints.ARRIVAL_RADIUS_STEP,
                Constants.Waypoints.MIN_ARRIVAL_RADIUS, Constants.Waypoints.MAX_ARRIVAL_RADIUS);
        radiusDecreaseButton.active = selectedArrivalRadius > Constants.Waypoints.MIN_ARRIVAL_RADIUS;
        radiusIncreaseButton.active = selectedArrivalRadius < Constants.Waypoints.MAX_ARRIVAL_RADIUS;
    }

    private void updateCargoControls() {
        if (operationButton != null) {
            operationButton.visible = selectedBehavior == BehaviorType.CARGO;
            operationButton.active = operationButton.visible;
        }
        if (selectLoadStationButton != null) {
            selectLoadStationButton.visible = selectedBehavior == BehaviorType.CARGO
                    && usesLoadStation(selectedOperation);
            selectLoadStationButton.active = selectLoadStationButton.visible;
        }
        if (selectUnloadStationButton != null) {
            selectUnloadStationButton.visible = selectedBehavior == BehaviorType.CARGO
                    && usesUnloadStation(selectedOperation);
            selectUnloadStationButton.active = selectUnloadStationButton.visible;
        }
        if (editLoadFilterButton != null) {
            editLoadFilterButton.visible = selectedBehavior == BehaviorType.CARGO
                    && usesLoadStation(selectedOperation);
            editLoadFilterButton.active = editLoadFilterButton.visible;
        }
        if (editUnloadFilterButton != null) {
            editUnloadFilterButton.visible = selectedBehavior == BehaviorType.CARGO
                    && usesUnloadStation(selectedOperation);
            editUnloadFilterButton.active = editUnloadFilterButton.visible;
        }
        // These anchors are calculated once with the widget layout so the buttons never drift away
        // from the station/filter detail rows when an operation has one or two transfer sections.
        if (selectUnloadStationButton != null) {
            selectUnloadStationButton.setY(unloadStationYPos);
        }
        if (editUnloadFilterButton != null) {
            editUnloadFilterButton.setY(unloadStationYPos);
        }
        if (selectLoadStationButton != null) {
            selectLoadStationButton.setY(loadStationYPos);
        }
        if (editLoadFilterButton != null) {
            editLoadFilterButton.setY(loadStationYPos);
        }
        moveWaypoint(0);
        if (radiusDecreaseButton != null) {
            adjustRadius(0);
        }
    }

    private void save() {
        WaypointAction action = selectedAction();
        if (action instanceof WaypointAction.Cargo && !hasRequiredStations()) {
            validationError = Component.translatable("message.farandwide.cargo_station_required");
            return;
        }
        if (action instanceof WaypointAction.Cargo cargo && conflictsWithRoute(cargo.behavior())) {
            validationError = Component.translatable("message.farandwide.operation.same_cargo_station");
            return;
        }
        if (existingWaypoint != null && existingWaypoint.action() instanceof WaypointAction.Cargo
                && action instanceof WaypointAction.Normal) {
            minecraft.setScreenAndShow(new ConfirmScreen(
                    confirmed -> {
                        if (confirmed) {
                            submit(action);
                        } else {
                            minecraft.setScreenAndShow(this);
                        }
                    },
                    Component.translatable("screen.farandwide.cargo_waypoint.discard_title"),
                    Component.translatable("screen.farandwide.cargo_waypoint.discard_message")));
            return;
        }
        submit(action);
    }

    private WaypointAction selectedAction() {
        if (selectedBehavior == BehaviorType.NORMAL) {
            return WaypointAction.normal();
        }
        return WaypointAction.cargo(new CargoBehavior(
                selectedOperation, loadFilter, unloadFilter, selectedLoadStation, selectedUnloadStation));
    }

    void setSelectedStation(CargoStationSelector.Role role, CargoStationBinding station) {
        validationError = null;
        if (role == CargoStationSelector.Role.LOAD) {
            selectedLoadStation = Optional.of(station);
        } else {
            selectedUnloadStation = Optional.of(station);
        }
    }

    void setSelectedFilter(boolean loading, CargoFilter filter) {
        validationError = null;
        if (loading) {
            loadFilter = filter;
            loadFilterScroll = 0;
        } else {
            unloadFilter = filter;
            unloadFilterScroll = 0;
        }
    }

    boolean isStationWithinArrivalRadius(CargoStationBinding station) {
        return WaypointProximity.isWithinArrivalRadius(proposedPosition, selectedArrivalRadius, station.position());
    }

    void stationSelectionCancelled() {
        if (minecraft.player != null) {
            minecraft.player.sendOverlayMessage(Component.translatable("message.farandwide.cargo_station_selection_cancelled"));
        }
    }

    private void submit(WaypointAction action) {
        if (existingWaypoint == null) {
            RouteManager.createWaypoint(route, proposedPosition, proposedDimension, action, selectedArrivalRadius);
        } else {
            RouteManager.replaceWaypoint(route, new Waypoint(
                    existingWaypoint.id(), proposedPosition, proposedDimension, action, selectedArrivalRadius), targetPosition);
        }
        super.onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (selectedBehavior == BehaviorType.CARGO && validationError == null && verticalAmount != 0) {
            if (!loadFilter.isAll() && isOverFilterStrip(mouseX, mouseY, true)) {
                loadFilterScroll = scrollFilter(loadFilter, loadFilterScroll, verticalAmount > 0 ? -1 : 1, true);
                return true;
            }
            if (!unloadFilter.isAll() && isOverFilterStrip(mouseX, mouseY, false)) {
                unloadFilterScroll = scrollFilter(unloadFilter, unloadFilterScroll, verticalAmount > 0 ? -1 : 1, false);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private boolean hasRequiredStations() {
        return switch (selectedOperation) {
            case LOAD -> selectedLoadStation.isPresent();
            case UNLOAD -> selectedUnloadStation.isPresent();
            case UNLOAD_THEN_LOAD -> selectedLoadStation.isPresent() && selectedUnloadStation.isPresent();
        };
    }

    private boolean conflictsWithRoute(CargoBehavior proposedBehavior) {
        return proposedBehavior.usesSameStation() || route.getWaypoints().stream()
                .filter(waypoint -> existingWaypoint == null || waypoint.id() != existingWaypoint.id())
                .filter(waypoint -> waypoint.action() instanceof WaypointAction.Cargo)
                .map(waypoint -> ((WaypointAction.Cargo) waypoint.action()).behavior())
                .anyMatch(proposedBehavior::conflictsWithOppositeRole);
    }

    private Component stationDescription(Optional<CargoStationBinding> binding, String direction) {
        return binding
                .<Component>map(station -> Component.translatable(
                        "screen.farandwide.cargo_waypoint." + direction + "_station_selected",
                        station.position().getX(), station.position().getY(), station.position().getZ(),
                        station.accessSide().getSerializedName()))
                .orElseGet(() -> Component.translatable(
                        "screen.farandwide.cargo_waypoint." + direction + "_station_unselected"));
    }

    private int extractFilterItemStrip(GuiGraphicsExtractor graphics, CargoFilter filter,
            int x, int y, int width, int scroll, int mouseX, int mouseY) {
        int visibleItems = Math.max(1, width / FILTER_ITEM_SLOT_SIZE);
        int maximumScroll = Math.max(0, filter.itemIds().size() - visibleItems);
        int clampedScroll = Math.clamp(scroll, 0, maximumScroll);
        int visibleCount = Math.min(visibleItems, filter.itemIds().size() - clampedScroll);
        for (int index = 0; index < visibleCount; index++) {
            Identifier itemId = filter.itemIds().get(clampedScroll + index);
            int itemX = x + index * FILTER_ITEM_SLOT_SIZE;
            boolean hovered = mouseX >= itemX && mouseX < itemX + FILTER_ITEM_SLOT_SIZE
                    && mouseY >= y && mouseY < y + FILTER_ITEM_SLOT_SIZE;
            graphics.fill(itemX, y, itemX + FILTER_ITEM_SLOT_SIZE - 2, y + FILTER_ITEM_SLOT_SIZE - 2,
                    hovered ? 0xFF555555 : 0xFF292929);
            graphics.outline(itemX, y, FILTER_ITEM_SLOT_SIZE - 2, FILTER_ITEM_SLOT_SIZE - 2, 0xFF777777);

            Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
            if (item != null) {
                graphics.item(item.getDefaultInstance(), itemX + 2, y + 2);
            } else {
                graphics.centeredText(font, "?", itemX + FILTER_ITEM_SIZE / 2 + 2, y + 6, 0xFFFFAA00);
            }
            if (hovered) {
                ArrayList<Component> tooltip = new ArrayList<>();
                tooltip.add(item == null ? Component.literal(itemId.toString()) : item.getDefaultInstance().getHoverName());
                tooltip.add(Component.literal(itemId.toString()).withStyle(ChatFormatting.GRAY));
                if (item == null) {
                    tooltip.add(Component.translatable("screen.farandwide.cargo_filter.item_unavailable")
                            .withStyle(ChatFormatting.GOLD));
                }
                graphics.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
            }
        }
        if (clampedScroll > 0) {
            graphics.text(font, "‹", x - 8, y + 5, 0xFFAAAAAA);
        }
        if (clampedScroll < maximumScroll) {
            graphics.text(font, "›", x + visibleItems * FILTER_ITEM_SLOT_SIZE, y + 5, 0xFFAAAAAA);
        }
        return clampedScroll;
    }

    private boolean isOverFilterStrip(double mouseX, double mouseY, boolean loading) {
        if (selectedBehavior != BehaviorType.CARGO
                || (loading && !usesLoadStation(selectedOperation))
                || (!loading && !usesUnloadStation(selectedOperation))) {
            return false;
        }
        int stripY = (loading ? loadFilterYPos : unloadFilterYPos) + 14;
        Component label = filterLabel(loading);
        int stripX = filterStripX(editorLeft, label);
        return mouseX >= stripX && mouseX < stripX + filterStripWidth(label)
                && mouseY >= stripY && mouseY < stripY + FILTER_ITEM_SLOT_SIZE;
    }

    private Component filterLabel(boolean loading) {
        return Component.translatable("screen.farandwide.cargo_waypoint."
                + (loading ? "load_filter_label" : "unload_filter_label"));
    }

    private int filterStripX(int left, Component label) {
        return left + font.width(label) + 4;
    }

    private int filterStripWidth(Component label) {
        return CONTROL_WIDTH - font.width(label) - 4;
    }

    private int scrollFilter(CargoFilter filter, int scroll, int amount, boolean loading) {
        int visibleItems = Math.max(1, filterStripWidth(filterLabel(loading)) / FILTER_ITEM_SLOT_SIZE);
        int maximumScroll = Math.max(0, filter.itemIds().size() - visibleItems);
        return Math.clamp(scroll + amount, 0, maximumScroll);
    }

    private static boolean usesLoadStation(CargoOperation operation) {
        return operation != CargoOperation.UNLOAD;
    }

    private static boolean usesUnloadStation(CargoOperation operation) {
        return operation != CargoOperation.LOAD;
    }

    private static Component operationName(CargoOperation operation) {
        return Component.translatable("cargo_operation.farandwide."
                + operation.name().toLowerCase(java.util.Locale.ROOT));
    }

    private enum BehaviorType {
        NORMAL,
        CARGO;

        Component displayName() {
            return Component.translatable("waypoint_behavior.farandwide."
                    + name().toLowerCase(java.util.Locale.ROOT));
        }
    }
}
