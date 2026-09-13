package com.lastcallsoftware.farandwide.route.client;

import com.lastcallsoftware.farandwide.Constants;
import com.lastcallsoftware.farandwide.client.FarAndWideScreen;
import com.lastcallsoftware.farandwide.route.OrderLine;
import com.lastcallsoftware.farandwide.route.OrderResult;
import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.Waypoint;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/** Builds and submits an order with side-by-side virtual source and selection inventories. */
@NonNullByDefault
public final class OrderPlacementScreen extends FarAndWideScreen {
    private static final int SLOT_SIZE = 22;
    private static final int ITEM_LABEL_TOP = 80;
    private static final int SEARCH_TOP = 92;
    private static final int GRID_TOP = 116;

    private int routeId = RouteManager.getCurrentRouteId();
    private int originId;
    private int destinationId;
    private String search = "";
    private int availableScrollRow;
    private int selectedScrollRow;
    private int selectionRouteId;
    private int selectionOriginId;
    private int requestedRouteId;
    private int requestedOriginId;
    private long requestedAfterRevision;
    private Map<ItemResource, Integer> pendingSelection = Map.of();
    private @Nullable OrderItemSelectionState selectionState;
    private @Nullable Button submitButton;
    private OrderItemSelectionState.@Nullable Side draggedScrollbar;
    private double scrollbarDragOffset;
    private long routeRevision;
    private @Nullable UUID submittedId;

    public OrderPlacementScreen() {
        super(Component.translatable("screen.farandwide.order.place_title"));
    }

    @Override protected void init() {
        RouteManager.getRoutes();
        chooseEndpoints();
        reconcileSelectionSource();
        requestAvailabilityIfNeeded();
        routeRevision = RouteManager.getRouteStateRevision();
        int left = left();
        int column = columnWidth();
        Route route = route();
        Component routeLabel = route == null ? Component.translatable("screen.farandwide.order.no_routes")
                : Component.literal(route.getName() + "  ›");
        addRenderableWidget(Button.builder(shorten(routeLabel, panelWidth() - 12), button -> {
            returnCarried();
            List<Route> choices = OrderScreenSupport.eligibleRoutes();
            int current = choices.indexOf(route());
            if (!choices.isEmpty()) routeId = choices.get((current + 1) % choices.size()).getId();
            originId = destinationId = 0;
            resetSelectionSource();
            rebuild();
        }).bounds(left, 30, panelWidth(), 20).tooltip(Tooltip.create(routeLabel)).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.farandwide.order.origin",
                OrderScreenSupport.ordinal(route, originId)), button -> {
            returnCarried();
            List<Waypoint> choices = OrderScreenSupport.origins(route());
            if (!choices.isEmpty()) originId = choices.get((choices.indexOf(origin()) + 1) % choices.size()).id();
            destinationId = 0;
            resetSelectionSource();
            rebuild();
        }).bounds(left, 54, column, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.farandwide.order.destination",
                OrderScreenSupport.ordinal(route, destinationId)), button -> {
            returnCarried();
            List<Waypoint> choices = OrderScreenSupport.destinations(route(), origin());
            int current = -1;
            for (int index = 0; index < choices.size(); index++) if (choices.get(index).id() == destinationId) current = index;
            if (!choices.isEmpty()) destinationId = choices.get((current + 1) % choices.size()).id();
            rebuild();
        }).bounds(right(), 54, column, 20).build());
        EditBox searchField = addRenderableWidget(new EditBox(font, left, SEARCH_TOP, column, 20,
                Component.translatable("screen.farandwide.cargo_filter.search")));
        searchField.setHint(Component.translatable("screen.farandwide.cargo_filter.search_hint"));
        searchField.setValue(search);
        searchField.setResponder(value -> { search = value; availableScrollRow = 0; });
        submitButton = addRenderableWidget(Button.builder(Component.translatable("screen.farandwide.order.place"), button -> {
            returnCarried();
            OrderItemSelectionState state = selectionState;
            if (state == null) return;
            submittedId = RouteManager.placeOrder(routeId, originId, destinationId, state.selected().entrySet().stream()
                    .map(entry -> new OrderLine(entry.getKey(), entry.getValue(), 0)).toList());
            updateActions();
        }).bounds(width / 2 - 105, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(width / 2 + 5, height - 28, 100, 20).build());
        updateActions();
    }

    @Override public void tick() {
        super.tick();
        if (routeRevision != RouteManager.getRouteStateRevision()) {
            rebuild();
            return;
        }
        if (selectionState == null && routeId == requestedRouteId && originId == requestedOriginId
                && RouteManager.getAvailableOrderItemsRevision() > requestedAfterRevision
                && RouteManager.hasAvailableOrderItems(routeId, originId)) {
            Map<ItemResource, Integer> stock = new LinkedHashMap<>();
            RouteManager.getAvailableOrderItems(routeId, originId)
                    .forEach(item -> stock.put(item.resource(), item.quantity()));
            selectionState = new OrderItemSelectionState(stock, pendingSelection);
            selectionRouteId = routeId;
            selectionOriginId = originId;
            pendingSelection = Map.of();
            clampScrolls();
        }
        RouteManager.OrderFeedback feedback = RouteManager.getOrderFeedback();
        if (submittedId != null && feedback != null && submittedId.equals(feedback.id())
                && feedback.result() == OrderResult.PLACED) {
            minecraft.setScreenAndShow(new OrderTrackingScreen(submittedId));
            return;
        }
        updateActions();
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        OrderItemSelectionState state = selectionState;
        if (state == null || (event.button() != 0 && event.button() != 1)) return false;
        OrderItemSelectionState.Side scrollbar = scrollbarAt(event.x(), event.y());
        if (scrollbar != null && event.button() == 0) {
            draggedScrollbar = scrollbar;
            int thumbTop = scrollbarThumbTop(scrollbar);
            int thumbHeight = scrollbarThumbHeight(scrollbar);
            scrollbarDragOffset = event.y() >= thumbTop && event.y() < thumbTop + thumbHeight
                    ? event.y() - thumbTop : thumbHeight / 2.0;
            setScrollFromThumb(scrollbar, event.y() - scrollbarDragOffset);
            return true;
        }
        OrderItemSelectionState.Side side = sideAt(event.x(), event.y());
        if (side == null) {
            state.returnCarried();
            return true;
        }
        ItemResource target = entryAt(side, event.x(), event.y());
        if (event.hasShiftDown() && target != null) state.quickMove(side, target);
        else state.click(side, target, event.button());
        clampScrolls();
        updateActions();
        return true;
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggedScrollbar != null && event.button() == 0) {
            setScrollFromThumb(draggedScrollbar, event.y() - scrollbarDragOffset);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (draggedScrollbar != null) {
            draggedScrollbar = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        OrderItemSelectionState.Side side = sideAt(mouseX, mouseY);
        if (side != null && verticalAmount != 0) {
            int amount = verticalAmount > 0 ? -1 : 1;
            if (side == OrderItemSelectionState.Side.AVAILABLE) availableScrollRow += amount;
            else selectedScrollRow += amount;
            clampScrolls();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("screen.farandwide.order.available_items"), left(), ITEM_LABEL_TOP,
                0xFFFFFFFF);
        graphics.text(font, Component.translatable("screen.farandwide.order.items", selectedEntries().size(),
                Constants.Orders.MAX_LINES), right(), ITEM_LABEL_TOP, 0xFFFFFFFF);
        extractGridBackground(graphics, OrderItemSelectionState.Side.AVAILABLE);
        extractGridBackground(graphics, OrderItemSelectionState.Side.SELECTED);
        OrderItemSelectionState state = selectionState;
        if (state == null) {
            graphics.centeredText(font, Component.translatable("screen.farandwide.order.loading_items"),
                    left() + columnWidth() / 2, GRID_TOP + 8, 0xFFAAAAAA);
        } else {
            List<Map.Entry<ItemResource, Integer>> available = availableEntries();
            extractItems(graphics, OrderItemSelectionState.Side.AVAILABLE, available, availableScrollRow, mouseX, mouseY);
            extractItems(graphics, OrderItemSelectionState.Side.SELECTED, selectedEntries(), selectedScrollRow, mouseX, mouseY);
            extractScrollbar(graphics, OrderItemSelectionState.Side.AVAILABLE);
            extractScrollbar(graphics, OrderItemSelectionState.Side.SELECTED);
            if (available.isEmpty()) {
                for (var line : font.split(Component.translatable("screen.farandwide.order.no_available_items"),
                        gridWidth() - 6)) {
                    graphics.centeredText(font, line, left() + gridWidth() / 2, GRID_TOP + 8, 0xFFAAAAAA);
                    break;
                }
            }
            extractCarriedItem(graphics, state.carried(), mouseX, mouseY);
        }
        @Nullable Component message = null;
        int color = 0xFFAAAAAA;
        var feedback = RouteManager.getOrderFeedback();
        if (RouteManager.isOrderRequestPending()) {
            message = Component.translatable("screen.farandwide.order.submitting");
        } else if (submittedId != null && feedback != null && submittedId.equals(feedback.id())) {
            message = feedback.message();
            color = 0xFFFFAA00;
        }
        if (message != null) {
            int y = height - 53;
            for (var line : font.split(message, panelWidth())) {
                graphics.text(font, line, left(), y, color);
                y += font.lineHeight;
                if (y >= height - 29) break;
            }
        }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreenAndShow(new OrderTrackingScreen()); }

    private void extractGridBackground(GuiGraphicsExtractor graphics, OrderItemSelectionState.Side side) {
        int gridLeft = gridLeft(side);
        int gridHeight = visibleRows() * SLOT_SIZE;
        graphics.fill(gridLeft - 2, GRID_TOP - 2, gridLeft + gridWidth(), GRID_TOP + gridHeight, 0xFF171717);
        graphics.outline(gridLeft - 2, GRID_TOP - 2, gridWidth() + 2, gridHeight + 2, 0xFF777777);
        for (int row = 0; row < visibleRows(); row++) for (int column = 0; column < columns(); column++) {
            int x = gridLeft + column * SLOT_SIZE;
            int y = GRID_TOP + row * SLOT_SIZE;
            graphics.fill(x, y, x + SLOT_SIZE - 2, y + SLOT_SIZE - 2, 0xFF292929);
            graphics.outline(x, y, SLOT_SIZE - 2, SLOT_SIZE - 2, 0xFF555555);
        }
    }

    private void extractItems(GuiGraphicsExtractor graphics, OrderItemSelectionState.Side side,
            List<Map.Entry<ItemResource, Integer>> entries, int scrollRow, int mouseX, int mouseY) {
        int first = scrollRow * columns();
        int last = Math.min(entries.size(), first + visibleRows() * columns());
        for (int index = first; index < last; index++) {
            int position = index - first;
            int x = gridLeft(side) + position % columns() * SLOT_SIZE;
            int y = GRID_TOP + position / columns() * SLOT_SIZE;
            Map.Entry<ItemResource, Integer> entry = entries.get(index);
            boolean hovered = mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE;
            extractItem(graphics, entry.getKey(), entry.getValue(), x, y);
            if (hovered) graphics.setComponentTooltipForNextFrame(font,
                    List.of(OrderScreenSupport.itemName(entry.getKey())), mouseX, mouseY);
        }
    }

    private void extractCarriedItem(GuiGraphicsExtractor graphics, OrderItemSelectionState.@Nullable Carried carried,
            int mouseX, int mouseY) {
        if (carried != null) extractItem(graphics, carried.resource(), carried.quantity(), mouseX - 8, mouseY - 8);
    }

    private void extractItem(GuiGraphicsExtractor graphics, ItemResource resource, int quantity, int x, int y) {
        var stack = resource.toStack();
        graphics.item(stack, x + 2, y + 2);
        graphics.itemDecorations(font, stack, x + 2, y + 2, Integer.toString(quantity));
    }

    private void extractScrollbar(GuiGraphicsExtractor graphics, OrderItemSelectionState.Side side) {
        int x = scrollbarLeft(side);
        int trackHeight = visibleRows() * SLOT_SIZE - 2;
        graphics.fill(x, GRID_TOP, x + 5, GRID_TOP + trackHeight, 0xFF202020);
        graphics.fill(x, scrollbarThumbTop(side), x + 5, scrollbarThumbTop(side) + scrollbarThumbHeight(side),
                maximumScroll(side) > 0 ? 0xFFAAAAAA : 0xFF555555);
    }

    private @Nullable ItemResource entryAt(OrderItemSelectionState.Side side, double mouseX, double mouseY) {
        int column = ((int) mouseX - gridLeft(side)) / SLOT_SIZE;
        int row = ((int) mouseY - GRID_TOP) / SLOT_SIZE;
        int scroll = side == OrderItemSelectionState.Side.AVAILABLE ? availableScrollRow : selectedScrollRow;
        List<Map.Entry<ItemResource, Integer>> entries = side == OrderItemSelectionState.Side.AVAILABLE
                ? availableEntries() : selectedEntries();
        int index = (scroll + row) * columns() + column;
        return index >= 0 && index < entries.size() ? entries.get(index).getKey() : null;
    }

    private OrderItemSelectionState.@Nullable Side sideAt(double x, double y) {
        if (y < GRID_TOP || y >= GRID_TOP + visibleRows() * SLOT_SIZE) return null;
        for (OrderItemSelectionState.Side side : OrderItemSelectionState.Side.values()) {
            int left = gridLeft(side);
            if (x >= left && x < left + gridWidth()) return side;
        }
        return null;
    }

    private OrderItemSelectionState.@Nullable Side scrollbarAt(double x, double y) {
        if (y < GRID_TOP || y >= GRID_TOP + visibleRows() * SLOT_SIZE - 2) return null;
        for (OrderItemSelectionState.Side side : OrderItemSelectionState.Side.values()) {
            int left = scrollbarLeft(side);
            if (x >= left && x < left + 5) return side;
        }
        return null;
    }

    private void setScrollFromThumb(OrderItemSelectionState.Side side, double thumbTop) {
        int maximum = maximumScroll(side);
        int travel = visibleRows() * SLOT_SIZE - 2 - scrollbarThumbHeight(side);
        int scroll = travel <= 0 ? 0 : (int) Math.round((thumbTop - GRID_TOP) * maximum / travel);
        if (side == OrderItemSelectionState.Side.AVAILABLE) availableScrollRow = Math.clamp(scroll, 0, maximum);
        else selectedScrollRow = Math.clamp(scroll, 0, maximum);
    }

    private int scrollbarThumbTop(OrderItemSelectionState.Side side) {
        int maximum = maximumScroll(side);
        int scroll = side == OrderItemSelectionState.Side.AVAILABLE ? availableScrollRow : selectedScrollRow;
        int travel = visibleRows() * SLOT_SIZE - 2 - scrollbarThumbHeight(side);
        return GRID_TOP + (maximum == 0 ? 0 : Math.round((float) scroll * travel / maximum));
    }

    private int scrollbarThumbHeight(OrderItemSelectionState.Side side) {
        int trackHeight = visibleRows() * SLOT_SIZE - 2;
        int contentRows = contentRows(side);
        return contentRows <= visibleRows() ? trackHeight : Math.max(8, trackHeight * visibleRows() / contentRows);
    }

    private int maximumScroll(OrderItemSelectionState.Side side) {
        return Math.max(0, contentRows(side) - visibleRows());
    }

    private int contentRows(OrderItemSelectionState.Side side) {
        int size = side == OrderItemSelectionState.Side.AVAILABLE ? availableEntries().size() : selectedEntries().size();
        return Math.max(1, (size + columns() - 1) / columns());
    }

    private List<Map.Entry<ItemResource, Integer>> availableEntries() {
        OrderItemSelectionState state = selectionState;
        if (state == null) return List.of();
        String query = search.trim().toLowerCase(Locale.ROOT);
        return sorted(state.available()).stream()
                .filter(entry -> entry.getKey().typeHolder().getRegisteredName().contains(query)
                        || entry.getKey().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private List<Map.Entry<ItemResource, Integer>> selectedEntries() {
        return selectionState == null ? List.of() : sorted(selectionState.selected());
    }

    private static List<Map.Entry<ItemResource, Integer>> sorted(Map<ItemResource, Integer> quantities) {
        return quantities.entrySet().stream().sorted(Comparator
                .comparing(entry -> entry.getKey().getHoverName().getString().toLowerCase(Locale.ROOT)))
                .toList();
    }

    private void chooseEndpoints() {
        List<Route> routes = OrderScreenSupport.eligibleRoutes();
        if (routes.stream().noneMatch(route -> route.getId() == routeId)) routeId = routes.isEmpty() ? 0 : routes.getFirst().getId();
        List<Waypoint> origins = OrderScreenSupport.origins(route());
        if (origins.stream().noneMatch(waypoint -> waypoint.id() == originId)) originId = origins.isEmpty() ? 0 : origins.getFirst().id();
        List<Waypoint> destinations = OrderScreenSupport.destinations(route(), origin());
        if (destinations.stream().noneMatch(waypoint -> waypoint.id() == destinationId)) {
            destinationId = destinations.isEmpty() ? 0 : destinations.getLast().id();
        }
    }

    private void reconcileSelectionSource() {
        if (selectionState != null && (selectionRouteId != routeId || selectionOriginId != originId)) resetSelectionSource();
    }

    private void resetSelectionSource() {
        returnCarried();
        if (selectionState != null) pendingSelection = selectionState.selected();
        selectionState = null;
        selectionRouteId = selectionOriginId = 0;
        requestedRouteId = requestedOriginId = 0;
        availableScrollRow = selectedScrollRow = 0;
    }

    private void requestAvailabilityIfNeeded() {
        if (selectionState != null || routeId <= 0 || originId <= 0
                || requestedRouteId == routeId && requestedOriginId == originId) return;
        requestedRouteId = routeId;
        requestedOriginId = originId;
        requestedAfterRevision = RouteManager.getAvailableOrderItemsRevision();
        RouteManager.refreshAvailableOrderItems(routeId, originId);
    }

    private void returnCarried() {
        if (selectionState != null) selectionState.returnCarried();
    }

    private void clampScrolls() {
        availableScrollRow = Math.clamp(availableScrollRow, 0, maximumScroll(OrderItemSelectionState.Side.AVAILABLE));
        selectedScrollRow = Math.clamp(selectedScrollRow, 0, maximumScroll(OrderItemSelectionState.Side.SELECTED));
    }

    private void updateActions() {
        OrderItemSelectionState state = selectionState;
        if (submitButton != null) submitButton.active = !RouteManager.isOrderRequestPending()
                && routeId > 0 && originId > 0 && destinationId > 0 && state != null && !state.selected().isEmpty();
    }

    private void rebuild() { clearWidgets(); init(); }
    private Component shorten(Component text, int width) { return Component.literal(font.plainSubstrByWidth(text.getString(), width)); }
    private int panelWidth() { return Math.min(600, width - 20); }
    private int left() { return (width - panelWidth()) / 2; }
    private int columnWidth() { return (panelWidth() - 12) / 2; }
    private int right() { return left() + columnWidth() + 12; }
    private int columns() { return Math.max(1, (columnWidth() - 7) / SLOT_SIZE); }
    private int gridWidth() { return columns() * SLOT_SIZE; }
    private int gridLeft(OrderItemSelectionState.Side side) {
        return side == OrderItemSelectionState.Side.AVAILABLE ? left() : right();
    }
    private int scrollbarLeft(OrderItemSelectionState.Side side) { return gridLeft(side) + gridWidth() + 1; }
    private int visibleRows() { return Math.max(1, (height - GRID_TOP - 48) / SLOT_SIZE); }
    private @Nullable Route route() { return RouteManager.getRoute(routeId); }
    private @Nullable Waypoint origin() {
        return OrderScreenSupport.origins(route()).stream().filter(waypoint -> waypoint.id() == originId).findFirst().orElse(null);
    }
}
