package com.lastcallsoftware.farandwide.route.client;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.Config;
import com.lastcallsoftware.farandwide.client.FarAndWideScreen;
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
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.Container;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import java.util.Optional;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;

/** Reusable create/edit screen for normal and cargo waypoint behavior. */
public final class CargoWaypointScreen extends FarAndWideScreen {
    private static final int CONTROL_WIDTH = Constants.Client.CARGO_WAYPOINT_CONTROL_WIDTH;
    private static final int FILTER_ITEM_SLOT_SIZE = 22;
    private static final int FILTER_ITEM_SIZE = 16;

    private final Route route;
    private final Waypoint existingWaypoint;
    private final Vec3 proposedPosition;
    private final Identifier proposedDimension;
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
    private final List<CargoStationBinding> sourceInventories = new ArrayList<>();
    private boolean editingSources;
    private int sourcePage;
    private Button sourcesButton;
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
        sourceInventories.addAll(initialCargo.sourceInventories());
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
        if (editingSources) {
            renderSourceWidgets();
            return;
        }
        sourcesButton = null;
        int left = (width - CONTROL_WIDTH) / 2;
        int top = height / 2 - 121 + font.lineHeight;
        int yPos = top + 8;
        editorLeft = left;
        editorTop = top;

        // Waypoint mode combines normal behavior and each cargo operation into one choice.
        addRenderableWidget(CycleButton
                .builder((CargoWaypointScreen.@NonNull WaypointMode mode) -> mode.displayName(),
                        WaypointMode.of(selectedBehavior, selectedOperation))
                .withValues(WaypointMode.values())
                .create(left, yPos, CONTROL_WIDTH, 20,
                        Component.translatable("screen.farandwide.cargo_waypoint.mode"),
                        (button, value) -> {
                            selectedBehavior = value.isCargo() ? BehaviorType.CARGO : BehaviorType.NORMAL;
                            if (value.isCargo()) selectedOperation = value.operation();
                            validationError = null;
                            // Cargo modes use different rows, so recreate the controls at their new positions.
                            rebuildEditor();
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

        // Keep the footer stable for every waypoint type by reserving the space
        // used by the largest configuration: Cargo Unload then Load.
        int footerYPos = yPos + 2 * (2 * ROW_HEIGHT + 14) + ROW_HEIGHT + 8;

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
            if (selectedBehavior == BehaviorType.CARGO) {
                sourcesButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.farandwide.sources.button", sourceInventories.size()), button -> {
                            editingSources = true;
                            rebuildEditor();
                        }).bounds(left, yPos, CONTROL_WIDTH, 20)
                                .tooltip(Tooltip.create(Component.translatable("screen.farandwide.sources.button.tooltip")))
                                .build());
                yPos += ROW_HEIGHT + 8;
            }
        }

        // Save and Cancel share the footer after the visible waypoint details.
        addRenderableWidget(Button.builder(
                Component.translatable("screen.farandwide.cargo_waypoint.save"),
                button -> save())
                .bounds(left + 41, footerYPos, 76, 20)
                .build());
        addRenderableWidget(Button.builder(
                Component.translatable("screen.farandwide.cargo_waypoint.cancel"),
                button -> onClose())
                .bounds(left + 123, footerYPos, 76, 20)
                .build());
        updateCargoControls();
    }

    private void rebuildEditor() {
        clearWidgets();
        renderWaypointEditorWidgets();
    }

    private int sourcesPerPage() {
        return Math.max(1, (height - 126) / ROW_HEIGHT);
    }

    private void renderSourceWidgets() {
        int left = (width - CONTROL_WIDTH) / 2;
        sourcePage = Math.clamp(sourcePage, 0, Math.max(0, (sourceInventories.size() - 1) / sourcesPerPage()));
        Button add = addRenderableWidget(Button.builder(Component.translatable("screen.farandwide.sources.add"),
                button -> CargoStationSelector.begin(this, CargoStationSelector.Role.SOURCE))
                .bounds(left, 46, CONTROL_WIDTH, 20).build());
        add.active = sourceInventories.size() < Constants.Orders.MAX_SOURCE_INVENTORIES;
        int first = sourcePage * sourcesPerPage();
        for (int index = first; index < Math.min(first + sourcesPerPage(), sourceInventories.size()); index++) {
            CargoStationBinding source = sourceInventories.get(index);
            int row = index - first;
            addRenderableWidget(Button.builder(Component.translatable("screen.farandwide.sources.remove"), button -> {
                sourceInventories.remove(source);
                rebuildEditor();
            }).bounds(left + 186, 76 + row * ROW_HEIGHT, 54, 20).build());
        }
        Button previous = addRenderableWidget(Button.builder(Component.literal("‹"), button -> {
            sourcePage--;
            rebuildEditor();
        }).bounds(left, height - 32, 30, 20).build());
        previous.active = sourcePage > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("›"), button -> {
            sourcePage++;
            rebuildEditor();
        }).bounds(left + 210, height - 32, 30, 20).build());
        next.active = (sourcePage + 1) * sourcesPerPage() < sourceInventories.size();
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> {
            editingSources = false;
            rebuildEditor();
        }).bounds(left + 70, height - 32, 100, 20).build());
    }

    @Override
    public void onClose() {
        if (editingSources) {
            editingSources = false;
            rebuildEditor();
        } else {
            super.onClose();
        }
    }

    /**
     * Renders the non-widget portion of the waypoint editor: its title, waypoint
     * metadata, station bindings, and the read-only item-filter strips. Buttons
     * and cycle controls render themselves through {@link #extractRenderState}.
     */
    private void renderWaypointEditorContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (editingSources) {
            int left = (width - CONTROL_WIDTH) / 2;
            graphics.centeredText(font, Component.translatable("screen.farandwide.sources.title", sourceInventories.size()),
                    width / 2, 12, 0xFFFFFFFF);
            graphics.centeredText(font, Component.translatable("screen.farandwide.sources.range", (int) Constants.Orders.SOURCE_RADIUS),
                    width / 2, 28, 0xFFAAAAAA);
            int first = sourcePage * sourcesPerPage();
            for (int index = first; index < Math.min(first + sourcesPerPage(), sourceInventories.size()); index++) {
                CargoStationBinding source = sourceInventories.get(index);
                Component description = Component.translatable("screen.farandwide.sources.description",
                        blockName(source), source.position().getX(), source.position().getY(), source.position().getZ(),
                        source.accessSide().getSerializedName());
                graphics.text(font, font.plainSubstrByWidth(description.getString(), 180), left,
                        82 + (index - first) * ROW_HEIGHT, 0xFFFFFFFF);
            }
            if (sourceInventories.isEmpty()) graphics.centeredText(font,
                    Component.translatable("screen.farandwide.sources.empty"), width / 2, 85, 0xFFAAAAAA);
            return;
        }
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
        if (sourcesButton != null) {
            sourcesButton.visible = selectedBehavior == BehaviorType.CARGO && usesLoadStation(selectedOperation);
            sourcesButton.active = sourcesButton.visible;
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
                    Component.translatable("screen.farandwide.cargo_waypoint.discard_message")) {
                @Override
                public boolean isPauseScreen() {
                    return Config.PAUSE_MOD_SCREENS.get();
                }
            });
            return;
        }
        submit(action);
    }

    private WaypointAction selectedAction() {
        if (selectedBehavior == BehaviorType.NORMAL) {
            return WaypointAction.normal();
        }
        return WaypointAction.cargo(new CargoBehavior(
                selectedOperation, loadFilter, unloadFilter, selectedLoadStation, selectedUnloadStation, sourceInventories));
    }

    void setSelectedStation(CargoStationSelector.Role role, CargoStationBinding station) {
        validationError = null;
        if (role == CargoStationSelector.Role.LOAD) {
            selectedLoadStation = Optional.of(station);
        } else if (role == CargoStationSelector.Role.UNLOAD) {
            selectedUnloadStation = Optional.of(station);
        } else {
            sourceInventories.removeIf(source -> source.position().equals(station.position()));
            if (sourceInventories.size() < Constants.Orders.MAX_SOURCE_INVENTORIES) sourceInventories.add(station);
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

    boolean isSourceWithinRange(CargoStationBinding station) {
        return WaypointProximity.isWithinArrivalRadius(proposedPosition, Constants.Orders.SOURCE_RADIUS, station.position());
    }

    /** Checks the client-visible block for the same inventory forms accepted by the server. */
    boolean isInventory(CargoStationBinding station) {
        return minecraft.level != null
                && (minecraft.level.getCapability(Capabilities.Item.BLOCK, station.position(), station.accessSide()) != null
                        || minecraft.level.getBlockEntity(station.position()) instanceof Container);
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
        if (editingSources) {
            if (verticalAmount != 0) {
                sourcePage += verticalAmount > 0 ? -1 : 1;
                rebuildEditor();
            }
            return true;
        }
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
                        blockName(station), station.position().getX(), station.position().getY(), station.position().getZ(),
                        station.accessSide().getSerializedName()))
                .orElseGet(() -> Component.translatable(
                        "screen.farandwide.cargo_waypoint." + direction + "_station_unselected"));
    }

    /** Returns the localized name of the block currently bound to a station or source. */
    private Component blockName(CargoStationBinding binding) {
        return minecraft.level == null
                ? Component.translatable("block.minecraft.air")
                : minecraft.level.getBlockState(binding.position()).getBlock().getName();
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

    private enum BehaviorType {
        NORMAL,
        CARGO
    }

    private enum WaypointMode {
        NORMAL,
        CARGO_UNLOAD,
        CARGO_LOAD,
        CARGO_UNLOAD_THEN_LOAD;

        static WaypointMode of(BehaviorType behavior, CargoOperation operation) {
            if (behavior == BehaviorType.NORMAL) return NORMAL;
            return switch (operation) {
                case UNLOAD -> CARGO_UNLOAD;
                case LOAD -> CARGO_LOAD;
                case UNLOAD_THEN_LOAD -> CARGO_UNLOAD_THEN_LOAD;
            };
        }

        boolean isCargo() { return this != NORMAL; }

        CargoOperation operation() {
            return switch (this) {
                case CARGO_UNLOAD -> CargoOperation.UNLOAD;
                case CARGO_LOAD -> CargoOperation.LOAD;
                case CARGO_UNLOAD_THEN_LOAD -> CargoOperation.UNLOAD_THEN_LOAD;
                case NORMAL -> throw new IllegalStateException("Normal waypoints have no cargo operation");
            };
        }

        Component displayName() {
            return Component.translatable("screen.farandwide.cargo_waypoint.mode."
                    + name().toLowerCase(java.util.Locale.ROOT));
        }
    }
}
