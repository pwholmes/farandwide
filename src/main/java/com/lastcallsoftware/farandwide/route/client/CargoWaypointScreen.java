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
    private static final int ACTION_WIDTH = 64;

    private final Route route;
    private final Waypoint existingWaypoint;
    private final Vec3 proposedPosition;
    private final Identifier proposedDimension;
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
    private boolean editingSourceInventories;
    private int sourcePage;
    private boolean loadTab;
    private int settingsScroll;
    private int settingsTop;
    private int settingsBottom;
    private int settingsHeight;
    private int sectionYPos;
    private int stationYPos;
    private int filterYPos;
    private int sourcesYPos;
    private int validationYPos;
    private int targetPosition;
    private double selectedArrivalRadius;
    private Component validationError;
    private int loadFilterScroll;
    private int unloadFilterScroll;
    /** Layout anchors calculated while placing the widgets and reused by the content renderer. */
    private int editorLeft;
    private int editorTop;
    private int footerYPos;

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
        if (editingSourceInventories) {
            int left = (width - CONTROL_WIDTH) / 2;
            graphics.fill(left - 10, 43, left + CONTROL_WIDTH + 10, height - 36, 0xCC000000);
        } else {
            graphics.fill(editorLeft - 10, editorTop + 12, editorLeft + CONTROL_WIDTH + 30,
                    footerYPos - 3, 0xCC000000);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        renderWaypointEditorContents(graphics, mouseX, mouseY);
    }

    private static final int ROW_HEIGHT = 23;
    private int waypointControlsYPos;

    /* Render Waypoint Editor Widgets */
    private void renderWaypointEditorWidgets() {
        if (editingSourceInventories) {
            renderSourceInventoryWidgets();
            return;
        }
        int left = (width - CONTROL_WIDTH) / 2;
        int top = Math.max(4, (height - 232) / 2);
        int yPos = top + 14;
        editorLeft = left;
        editorTop = top;

        // Waypoint mode combines normal behavior and each cargo operation into one choice.
        addRenderableWidget(CycleButton
                .builder((CargoWaypointScreen.@NonNull WaypointMode mode) -> mode.displayName(),
                        WaypointMode.of(selectedBehavior, selectedOperation))
                .withValues(WaypointMode.values())
                .withTooltip(mode -> Tooltip.create(mode == WaypointMode.CARGO_UNLOAD_THEN_LOAD
                        ? Component.translatable("screen.farandwide.cargo_waypoint.exchange_description") : mode.displayName()))
                .create(left, yPos, CONTROL_WIDTH, 20,
                        Component.translatable("screen.farandwide.cargo_waypoint.mode"),
                        (button, value) -> {
                            selectedBehavior = value.isCargo() ? BehaviorType.CARGO : BehaviorType.NORMAL;
                            if (value.isCargo()) {
                                if (value.operation() == CargoOperation.UNLOAD_THEN_LOAD
                                        && selectedOperation != CargoOperation.UNLOAD_THEN_LOAD) loadTab = false;
                                selectedOperation = value.operation();
                            }
                            validationError = null;
                            settingsScroll = 0;
                            // Rebuild the shared settings area for the chosen waypoint mode.
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

        // Keep the footer stable for every waypoint type, including Normal, and across cargo tabs.
        footerYPos = Math.min(height - 26, yPos + 142);
        settingsTop = yPos;
        settingsBottom = footerYPos - 5;
        if (selectedBehavior == BehaviorType.CARGO) renderCargoWidgets(left);

        // Save and Cancel share the fixed footer below the reserved settings area.
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
        moveWaypoint(0);
        if (radiusDecreaseButton != null) adjustRadius(0);
    }

    private boolean showingLoad() {
        return selectedOperation == CargoOperation.LOAD
                || (selectedOperation == CargoOperation.UNLOAD_THEN_LOAD && loadTab);
    }

    private void renderCargoWidgets(int left) {
        settingsHeight = 23 + 32 + 38 + (usesLoadStation(selectedOperation) ? 30 : 0);
        if (validationError != null) settingsHeight += font.split(validationError, CONTROL_WIDTH).size() * font.lineHeight + 4;
        settingsScroll = Math.clamp(settingsScroll, 0, maximumSettingsScroll());
        sectionYPos = settingsTop - settingsScroll;
        stationYPos = sectionYPos + 23;
        filterYPos = stationYPos + 32;
        sourcesYPos = filterYPos + 38;
        validationYPos = usesLoadStation(selectedOperation) ? sourcesYPos + 30 : sourcesYPos;
        boolean loading = showingLoad();
        Optional<CargoStationBinding> station = loading ? selectedLoadStation : selectedUnloadStation;
        if (selectedOperation == CargoOperation.UNLOAD_THEN_LOAD) {
            addOperationTab(left, false);
            addOperationTab(left + 124, true);
        }
        // The station action stays beside its current value for either cargo operation.
        addSettingsButton(Button.builder(Component.translatable("screen.farandwide.cargo_waypoint."
                        + (station.isPresent() ? "change_station" : "select_station")),
                button -> CargoStationSelector.begin(this,
                        loading ? CargoStationSelector.Role.LOAD : CargoStationSelector.Role.UNLOAD))
                .bounds(left + CONTROL_WIDTH - ACTION_WIDTH, stationYPos, ACTION_WIDTH, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.farandwide.cargo_waypoint.select_station.tooltip")))
                .build());
        // The filter action shares its row with the item summary and scrollable item icons.
        addSettingsButton(Button.builder(Component.translatable("screen.farandwide.cargo_waypoint.edit_items"),
                button -> minecraft.setScreenAndShow(new CargoFilterScreen(this, loading, loading ? loadFilter : unloadFilter)))
                .bounds(left + CONTROL_WIDTH - ACTION_WIDTH, filterYPos, ACTION_WIDTH, 20).build());
        if (usesLoadStation(selectedOperation)) {
            addSettingsButton(Button.builder(Component.translatable("screen.farandwide.source_inventories.button"), button -> {
                editingSourceInventories = true;
                rebuildEditor();
            }).bounds(left + CONTROL_WIDTH - ACTION_WIDTH, sourcesYPos, ACTION_WIDTH, 20)
                    .tooltip(Tooltip.create(Component.translatable("screen.farandwide.source_inventories.button.tooltip"))).build());
        }
        if (maximumSettingsScroll() > 0) {
            Button up = addRenderableWidget(Button.builder(Component.literal("↑"), button -> scrollSettings(-ROW_HEIGHT))
                    .bounds(left + CONTROL_WIDTH + 4, settingsTop, 20, 20)
                    .tooltip(Tooltip.create(Component.translatable("screen.farandwide.cargo_waypoint.scroll_up"))).build());
            up.active = settingsScroll > 0;
            Button down = addRenderableWidget(Button.builder(Component.literal("↓"), button -> scrollSettings(ROW_HEIGHT))
                    .bounds(left + CONTROL_WIDTH + 4, settingsBottom - 20, 20, 20)
                    .tooltip(Tooltip.create(Component.translatable("screen.farandwide.cargo_waypoint.scroll_down"))).build());
            down.active = settingsScroll < maximumSettingsScroll();
        }
    }

    private void addOperationTab(int x, boolean loading) {
        boolean missing = (loading ? selectedLoadStation : selectedUnloadStation).isEmpty();
        Component name = Component.translatable("screen.farandwide.cargo_waypoint.tab." + (loading ? "load" : "unload"));
        if (missing) name = name.copy().append(" !").withStyle(ChatFormatting.GOLD);
        Button tab = addSettingsButton(Button.builder(name, button -> {
            loadTab = loading;
            rebuildEditor();
        }).bounds(x, sectionYPos, 116, 20)
                .tooltip(Tooltip.create(Component.translatable(missing
                        ? "screen.farandwide.cargo_waypoint.station_required"
                        : "screen.farandwide.cargo_waypoint.exchange_description"))).build());
        tab.active = loading != loadTab;
    }

    private Button addSettingsButton(Button button) {
        // Offscreen controls cannot receive focus or clicks over the fixed header and footer.
        button.visible = button.getY() >= settingsTop && button.getY() + button.getHeight() <= settingsBottom;
        return addRenderableWidget(button);
    }

    private int maximumSettingsScroll() {
        return Math.max(0, settingsHeight - (settingsBottom - settingsTop));
    }

    private void scrollSettings(int amount) {
        settingsScroll = Math.clamp(settingsScroll + amount, 0, maximumSettingsScroll());
        rebuildEditor();
    }

    private void rebuildEditor() {
        clearWidgets();
        renderWaypointEditorWidgets();
    }

    private int sourceInventoriesPerPage() {
        return Math.max(1, (height - 126) / ROW_HEIGHT);
    }

    private void renderSourceInventoryWidgets() {
        int left = (width - CONTROL_WIDTH) / 2;
        sourcePage = Math.clamp(sourcePage, 0, Math.max(0, (sourceInventories.size() - 1) / sourceInventoriesPerPage()));
        Button add = addRenderableWidget(Button.builder(Component.translatable("screen.farandwide.source_inventories.add"),
                button -> CargoStationSelector.begin(this, CargoStationSelector.Role.SOURCE_INVENTORY))
                .bounds(left, 46, CONTROL_WIDTH, 20).build());
        add.active = sourceInventories.size() < Constants.Orders.MAX_SOURCE_INVENTORIES;
        int first = sourcePage * sourceInventoriesPerPage();
        for (int index = first; index < Math.min(first + sourceInventoriesPerPage(), sourceInventories.size()); index++) {
            CargoStationBinding source = sourceInventories.get(index);
            int row = index - first;
            addRenderableWidget(Button.builder(Component.translatable("screen.farandwide.source_inventories.remove"), button -> {
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
        next.active = (sourcePage + 1) * sourceInventoriesPerPage() < sourceInventories.size();
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> {
            editingSourceInventories = false;
            rebuildEditor();
        }).bounds(left + 70, height - 32, 100, 20).build());
    }

    @Override
    public void onClose() {
        if (editingSourceInventories) {
            editingSourceInventories = false;
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
        if (editingSourceInventories) {
            int left = (width - CONTROL_WIDTH) / 2;
            graphics.centeredText(font, Component.translatable("screen.farandwide.source_inventories.title", sourceInventories.size()),
                    width / 2, 12, 0xFFFFFFFF);
            graphics.centeredText(font, Component.translatable("screen.farandwide.source_inventories.range", (int) Constants.Orders.SOURCE_RADIUS),
                    width / 2, 28, 0xFFAAAAAA);
            int first = sourcePage * sourceInventoriesPerPage();
            for (int index = first; index < Math.min(first + sourceInventoriesPerPage(), sourceInventories.size()); index++) {
                CargoStationBinding source = sourceInventories.get(index);
                Component description = Component.translatable("screen.farandwide.source_inventories.description",
                        blockName(source), source.position().getX(), source.position().getY(), source.position().getZ(),
                        source.accessSide().getSerializedName());
                graphics.text(font, font.plainSubstrByWidth(description.getString(), 180), left,
                        82 + (index - first) * ROW_HEIGHT, 0xFFFFFFFF);
            }
            if (sourceInventories.isEmpty()) graphics.centeredText(font,
                    Component.translatable("screen.farandwide.source_inventories.empty"), width / 2, 85, 0xFFAAAAAA);
            return;
        }
        int left = editorLeft;
        int top = editorTop;

        graphics.text(font, title, (width - font.width(title)) / 2, top, 0xFFFFFFFF);

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
            graphics.enableScissor(left, settingsTop, left + CONTROL_WIDTH, settingsBottom);
            if (selectedOperation != CargoOperation.UNLOAD_THEN_LOAD) {
                graphics.text(font, Component.translatable("screen.farandwide.cargo_waypoint.section."
                        + (showingLoad() ? "load" : "unload")), left, sectionYPos + 5, 0xFFFFFFFF);
            }
            // Cargo Exchange defaults to unloading first; each tab keeps its station and filter together.
            renderCargoDetails(graphics, left, mouseX, mouseY);
            if (usesLoadStation(selectedOperation)) {
                graphics.text(font, Component.translatable("screen.farandwide.source_inventories.label"), left, sourcesYPos, 0xFFFFFFFF);
                String summaryKey = switch (sourceInventories.size()) {
                    case 0 -> "screen.farandwide.source_inventories.optional";
                    case 1 -> "screen.farandwide.source_inventories.summary_one";
                    default -> "screen.farandwide.source_inventories.summary";
                };
                graphics.text(font, Component.translatable(summaryKey, sourceInventories.size()),
                        left, sourcesYPos + 12, 0xFFAAAAAA);
            }
            if (validationError != null) {
                // Keep validation separate from current values so correcting a setting does not hide its details.
                int errorY = validationYPos;
                for (FormattedCharSequence line : font.split(validationError, CONTROL_WIDTH)) {
                    graphics.text(font, line, left, errorY, 0xFFFF5555);
                    errorY += font.lineHeight;
                }
            }
            graphics.disableScissor();
        }
    }

    private void renderCargoDetails(GuiGraphicsExtractor graphics, int left, int mouseX, int mouseY) {
        boolean loading = showingLoad();
        Optional<CargoStationBinding> station = loading ? selectedLoadStation : selectedUnloadStation;
        CargoFilter filter = loading ? loadFilter : unloadFilter;
        Component stationText = stationDescription(station, loading ? "load" : "unload");
        int detailWidth = CONTROL_WIDTH - ACTION_WIDTH - 8;
        graphics.text(font, Component.translatable("screen.farandwide.cargo_waypoint.station"), left, stationYPos,
                station.isEmpty() ? 0xFFFFAA00 : 0xFFFFFFFF);
        String stationSummary = stationText.getString();
        if (font.width(stationSummary) > detailWidth) {
            stationSummary = font.plainSubstrByWidth(stationSummary, detailWidth - font.width("...")) + "...";
        }
        graphics.text(font, stationSummary, left, stationYPos + 12, 0xFFAAAAAA);
        if (mouseY >= settingsTop && mouseY < settingsBottom && mouseX >= left && mouseX < left + detailWidth
                && mouseY >= stationYPos && mouseY < stationYPos + 26) {
            graphics.setComponentTooltipForNextFrame(font, List.of(stationText), mouseX, mouseY);
        }
        graphics.text(font, filterLabel(loading), left, filterYPos, 0xFFFFFFFF);
        if (filter.isAll()) {
            graphics.text(font, Component.translatable("screen.farandwide.cargo_filter.summary_all"),
                    left, filterYPos + 12, 0xFFAAAAAA);
        } else {
            Component summary = Component.translatable("screen.farandwide.cargo_filter.summary_only");
            graphics.text(font, summary, left, filterYPos + 17, 0xFFAAAAAA);
            int scroll = extractFilterItemStrip(graphics, filter, filterStripX(left, summary), filterYPos + 12,
                    filterStripWidth(summary), loading ? loadFilterScroll : unloadFilterScroll,
                    mouseX, mouseY >= settingsTop && mouseY < settingsBottom ? mouseY : -1);
            if (loading) loadFilterScroll = scroll;
            else unloadFilterScroll = scroll;
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

    private void save() {
        WaypointAction action = selectedAction();
        if (action instanceof WaypointAction.Cargo && !hasRequiredStations()) {
            loadTab = selectedUnloadStation.isPresent();
            validationError = Component.translatable("screen.farandwide.cargo_waypoint.station_required_for",
                    Component.translatable("screen.farandwide.cargo_waypoint.tab." + (showingLoad() ? "load" : "unload")));
            settingsScroll = 0;
            rebuildEditor();
            return;
        }
        if (action instanceof WaypointAction.Cargo cargo && conflictsWithRoute(cargo.behavior())) {
            validationError = Component.translatable("message.farandwide.operation.same_cargo_station");
            settingsScroll = Integer.MAX_VALUE;
            rebuildEditor();
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

    boolean isStationWithinRange(CargoStationBinding station) {
        return WaypointProximity.isWithinArrivalRadius(
                proposedPosition, Constants.Cargo.STATION_RADIUS, station.position());
    }

    boolean isSourceInventoryWithinRange(CargoStationBinding station) {
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
        if (editingSourceInventories) {
            if (verticalAmount != 0) {
                sourcePage += verticalAmount > 0 ? -1 : 1;
                rebuildEditor();
            }
            return true;
        }
        if (selectedBehavior == BehaviorType.CARGO && verticalAmount != 0
                && mouseY >= settingsTop && mouseY < settingsBottom) {
            if (!loadFilter.isAll() && isOverFilterStrip(mouseX, mouseY, true)) {
                loadFilterScroll = scrollFilter(loadFilter, loadFilterScroll, verticalAmount > 0 ? -1 : 1);
                return true;
            }
            if (!unloadFilter.isAll() && isOverFilterStrip(mouseX, mouseY, false)) {
                unloadFilterScroll = scrollFilter(unloadFilter, unloadFilterScroll, verticalAmount > 0 ? -1 : 1);
                return true;
            }
            if (maximumSettingsScroll() > 0) {
                scrollSettings(verticalAmount > 0 ? -ROW_HEIGHT : ROW_HEIGHT);
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
        if (selectedBehavior != BehaviorType.CARGO || loading != showingLoad()) {
            return false;
        }
        int stripY = filterYPos + 12;
        Component label = Component.translatable("screen.farandwide.cargo_filter.summary_only");
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
        return CONTROL_WIDTH - ACTION_WIDTH - font.width(label) - 16;
    }

    private int scrollFilter(CargoFilter filter, int scroll, int amount) {
        int visibleItems = Math.max(1, filterStripWidth(Component.translatable(
                "screen.farandwide.cargo_filter.summary_only")) / FILTER_ITEM_SLOT_SIZE);
        int maximumScroll = Math.max(0, filter.itemIds().size() - visibleItems);
        return Math.clamp(scroll + amount, 0, maximumScroll);
    }

    private static boolean usesLoadStation(CargoOperation operation) {
        return operation != CargoOperation.UNLOAD;
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
