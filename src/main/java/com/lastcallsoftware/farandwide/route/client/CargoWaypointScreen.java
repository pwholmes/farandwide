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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/** Reusable create/edit screen for normal and cargo waypoint behavior. */
public final class CargoWaypointScreen extends Screen {
    private static final int CONTROL_WIDTH = Constants.Client.CARGO_WAYPOINT_CONTROL_WIDTH;

    private final Route route;
    private final Waypoint existingWaypoint;
    private final Vec3 proposedPosition;
    private final Identifier proposedDimension;
    private CycleButton<CargoOperation> operationButton;
    private Button selectLoadStationButton;
    private Button selectUnloadStationButton;
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
        int left = (width - CONTROL_WIDTH) / 2;
        int top = height / 2 - 94;
        int yPos = top;

        // Behavior (Normal vs Cargo)
        addRenderableWidget(CycleButton
                .builder(BehaviorType::displayName, selectedBehavior)
                .withValues(BehaviorType.values())
                .create(left, yPos, CONTROL_WIDTH, 20,
                        Component.translatable("screen.farandwide.cargo_waypoint.behavior"),
                        (button, value) -> {
                            selectedBehavior = value;
                            validationError = null;
                            updateCargoControls();
                        }));
        yPos += 26;

        // Waypoint (Ordinal) Position and Radius
        if (existingWaypoint != null) {
            moveUpButton = addRenderableWidget(Button.builder(Component.literal("↑"), button -> moveWaypoint(-1))
                    .bounds(left, yPos, 20, 20)
                    .build());
            moveDownButton = addRenderableWidget(Button.builder(Component.literal("↓"), button -> moveWaypoint(1))
                    .bounds(left + 100, yPos, 20, 20)
                    .build());
            radiusDecreaseButton = addRenderableWidget(Button.builder(Component.literal("+"), button -> adjustRadius(-1))
                    .bounds(left + 124, yPos, 20, 20)
                    .build());
            radiusIncreaseButton = addRenderableWidget(Button.builder(Component.literal("−"), button -> adjustRadius(1))
                    .bounds(left + CONTROL_WIDTH - 20, yPos, 20, 20)
                    .build());
            yPos += 26;
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
                            updateCargoControls();
                        }));
        yPos += 26;

        // Load and unload station selection buttons.
        selectLoadStationButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.farandwide.cargo_waypoint.select_load_station"),
                button -> CargoStationSelector.begin(this, CargoStationSelector.Role.LOAD))
                .bounds(left, yPos, 116, 20)
                .build());
        selectUnloadStationButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.farandwide.cargo_waypoint.select_unload_station"),
                button -> CargoStationSelector.begin(this, CargoStationSelector.Role.UNLOAD))
                .bounds(left + 124, yPos, 116, 20)
                .build());
        yPos += 26;

        // Leave room for up to two station/filter detail rows.
        yPos += 52;

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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int left = (width - CONTROL_WIDTH) / 2;
        int top = height / 2 - 94;
        int yPos = top;
        yPos += 26; // Behavior row.

        graphics.text(font, title, (width - font.width(title)) / 2, top - 25, 0xFFFFFFFF);

        if (existingWaypoint != null) {
            Component position = Component.translatable(
                    "screen.farandwide.cargo_waypoint.position", targetPosition + 1, route.getWaypoints().size());
            graphics.centeredText(font, position, left + 60, yPos + 6, 0xFFFFFFFF);
            graphics.centeredText(font, Component.literal("Radius: %.1f".formatted(selectedArrivalRadius)),
                    left + 182, yPos + 6, 0xFFFFFFFF);
            yPos += 26;
        }
        yPos += 26; // Operation row.
        yPos += 26; // Station-selection row.

        if (selectedBehavior == BehaviorType.CARGO) {
            if (validationError != null) {
                for (FormattedCharSequence line : font.split(validationError, CONTROL_WIDTH)) {
                    graphics.text(font, line, (width - font.width(line)) / 2, yPos, 0xFFFF5555);
                    yPos += font.lineHeight;
                }
            } else {
                if (usesLoadStation(selectedOperation)) {
                    graphics.text(font, stationDescription(selectedLoadStation, "load"), left, yPos, 0xFFAAAAAA);
                    graphics.text(font, Component.translatable("screen.farandwide.cargo_waypoint.load_filter"),
                            left, yPos + 14, 0xFFAAAAAA);
                    yPos += 28;
                }
                if (usesUnloadStation(selectedOperation)) {
                    graphics.text(font, stationDescription(selectedUnloadStation, "unload"), left, yPos, 0xFFAAAAAA);
                    graphics.text(font, Component.translatable("screen.farandwide.cargo_waypoint.unload_filter"),
                            left, yPos + 14, 0xFFAAAAAA);
                }
            }
        }
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
