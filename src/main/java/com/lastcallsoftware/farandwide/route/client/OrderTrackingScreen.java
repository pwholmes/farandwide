package com.lastcallsoftware.farandwide.route.client;

import com.lastcallsoftware.farandwide.client.FarAndWideScreen;
import com.lastcallsoftware.farandwide.route.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/** Shows confirmed receipts; removing a record never changes cargo or route activity. */
@NonNullByDefault
public final class OrderTrackingScreen extends FarAndWideScreen {
    private @Nullable UUID selectedId;
    private int page;
    private int itemPage;
    private int filter;
    private int refreshTicks;
    private long orderRevision;
    private long routeRevision;
    private @Nullable Button cancelButton;

    public OrderTrackingScreen() { this(null); }
    public OrderTrackingScreen(@Nullable UUID selectedId) {
        super(Component.translatable("screen.farandwide.order.tracking_title"));
        this.selectedId = selectedId;
    }

    private int panelWidth() { return Math.min(600, width - 20); }
    private int left() { return (width - panelWidth()) / 2; }
    private int columnWidth() { return (panelWidth() - 12) / 2; }
    private int right() { return left() + columnWidth() + 12; }
    private int rows() { return Math.max(1, (height - 132) / 38); }
    private int itemRows() { return Math.max(1, (height - itemTop() - 57) / 20); }
    private int itemTop() {
        CargoOrder order = selected();
        if (order == null) return 105;
        Component warning = routeWarning(order);
        int legsHeight = order.legs().size() * font.lineHeight + 5;
        return 105 + legsHeight + (warning == null ? 0 : Math.min(2, font.split(warning, columnWidth()).size()) * font.lineHeight + 5);
    }
    private @Nullable CargoOrder selected() {
        return RouteManager.getOrders().stream().filter(order -> order.id().equals(selectedId)).findFirst().orElse(null);
    }
    private List<CargoOrder> filtered() {
        List<CargoOrder> orders = new ArrayList<>(RouteManager.getOrders().stream()
                .filter(order -> filter == 0 || order.delivered() == (filter == 2)).toList());
        java.util.Collections.reverse(orders);
        return orders;
    }

    @Override protected void init() {
        RouteManager.getRoutes();
        RouteManager.refreshOrders();
        refreshTicks = 60;
        buildWidgets();
    }

    private void rebuild() { clearWidgets(); buildWidgets(); }

    private void buildWidgets() {
        cancelButton = null;
        orderRevision = RouteManager.getOrderStateRevision();
        routeRevision = RouteManager.getRouteStateRevision();
        List<CargoOrder> orders = filtered();
        page = Math.clamp(page, 0, Math.max(0, (orders.size() - 1) / rows()));
        if (orders.stream().noneMatch(order -> order.id().equals(selectedId))) {
            selectedId = orders.isEmpty() ? null : orders.getFirst().id();
            itemPage = 0;
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.farandwide.order.filter." + filter), button -> {
            filter = (filter + 1) % 3;
            page = 0;
            rebuild();
        }).bounds(left(), 36, columnWidth(), 20).build());
        for (int row = 0; row < rows(); row++) {
            int index = page * rows() + row;
            if (index >= orders.size()) break;
            CargoOrder order = orders.get(index);
            Route route = RouteManager.getRoute(order.routeId());
            Component label = Component.translatable("screen.farandwide.order.reference", orderNumber(order))
                    .append(" ").append(Component.literal(route == null ? "?" : route.getName()));
            Button button = addRenderableWidget(Button.builder(shorten(label, columnWidth() - 12), clicked -> {
                selectedId = order.id();
                itemPage = 0;
                rebuild();
            }).bounds(left(), 64 + row * 38, columnWidth(), 20)
                    .tooltip(Tooltip.create(label.copy().append("\n").append(endpoints(order)))).build());
            button.active = !order.id().equals(selectedId);
        }
        Button previous = addRenderableWidget(Button.builder(Component.literal("‹"), button -> { page--; rebuild(); })
                .bounds(left(), height - 60, 28, 20).build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("›"), button -> { page++; rebuild(); })
                .bounds(left() + columnWidth() - 28, height - 60, 28, 20).build());
        next.active = (page + 1) * rows() < orders.size();
        CargoOrder selected = selected();
        itemPage = Math.clamp(itemPage, 0, selected == null ? 0 : Math.max(0, (selected.lines().size() - 1) / itemRows()));
        Button previousItems = addRenderableWidget(Button.builder(Component.literal("‹"), button -> { itemPage--; rebuild(); })
                .bounds(right(), height - 60, 28, 20).build());
        previousItems.active = itemPage > 0;
        Button nextItems = addRenderableWidget(Button.builder(Component.literal("›"), button -> { itemPage++; rebuild(); })
                .bounds(right() + columnWidth() - 28, height - 60, 28, 20).build());
        nextItems.active = selected != null && (itemPage + 1) * itemRows() < selected.lines().size();
        if (selected != null) {
            int cancelWidth = Math.min(110, columnWidth() / 2);
            Component cancelLabel = Component.translatable(selected.delivered()
                    ? "screen.farandwide.order.delete_tracking" : "screen.farandwide.order.cancel_tracking");
            cancelButton = addRenderableWidget(Button.builder(cancelLabel, button -> RouteManager.cancelOrder(selected.id()))
                    .bounds(right() + columnWidth() - cancelWidth, 33, cancelWidth, 20).build());
            cancelButton.active = !RouteManager.isOrderRequestPending();
        }
        int buttonWidth = Math.min(180, (panelWidth() - 6) / 2);
        int start = (width - buttonWidth * 2 - 6) / 2;
        boolean canPlaceOrder = !OrderScreenSupport.eligibleRoutes().isEmpty();
        Button placeButton = addRenderableWidget(Button.builder(Component.translatable("screen.farandwide.order.place"), button -> {
            if (!OrderScreenSupport.eligibleRoutes().isEmpty()) minecraft.setScreenAndShow(new OrderPlacementScreen());
        })
                .bounds(start, height - 28, buttonWidth, 20).build());
        placeButton.active = canPlaceOrder;
        if (!canPlaceOrder) placeButton.setTooltip(Tooltip.create(Component.translatable("screen.farandwide.order.no_routes")));
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(start + buttonWidth + 6, height - 28, buttonWidth, 20).build());
    }

    private Component shorten(Component text, int width) { return Component.literal(font.plainSubstrByWidth(text.getString(), width)); }
    private Component endpoints(CargoOrder order) {
        Route route = RouteManager.getRoute(order.routeId());
        return Component.translatable("screen.farandwide.order.endpoints", OrderScreenSupport.ordinal(route, order.originWaypointId()),
                OrderScreenSupport.ordinal(route, order.destinationWaypointId()));
    }

    /** Numbers follow creation order for the currently tracked orders. */
    private int orderNumber(CargoOrder order) {
        return RouteManager.getOrders().indexOf(order) + 1;
    }

    @Override public void tick() {
        super.tick();
        if (--refreshTicks <= 0) {
            refreshTicks = 60;
            RouteManager.refreshOrders();
        }
        if (orderRevision != RouteManager.getOrderStateRevision() || routeRevision != RouteManager.getRouteStateRevision()) rebuild();
        if (cancelButton != null) {
            cancelButton.active = selected() != null && !RouteManager.isOrderRequestPending();
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        List<CargoOrder> orders = filtered();
        if (orders.isEmpty()) {
            graphics.text(font, Component.translatable("screen.farandwide.order.empty"), left(), 68, 0xFFAAAAAA);
        }
        for (int row = 0; row < rows() && page * rows() + row < orders.size(); row++) {
            CargoOrder order = orders.get(page * rows() + row);
            int delivered = order.lines().stream().mapToInt((@NonNull OrderLine line) -> line.delivered()).sum();
            int requested = order.lines().stream().mapToInt((@NonNull OrderLine line) -> line.requested()).sum();
            Component status = order.delivered() ? Component.translatable("screen.farandwide.order.delivered")
                    : Component.translatable("screen.farandwide.order.progress", delivered, requested);
            graphics.text(font, shorten(status, columnWidth()), left() + 3, 87 + row * 38,
                    order.delivered() ? 0xFF55FF77 : 0xFFAAAAAA);
        }
        CargoOrder selected = selected();
        if (selected == null) return;
        graphics.text(font, Component.translatable("screen.farandwide.order.detail", orderNumber(selected)),
                right(), 36, 0xFFFFFFFF);
        Route selectedRoute = RouteManager.getRoute(selected.routeId());
        graphics.text(font, shorten(Component.translatable("screen.farandwide.order.route",
                selectedRoute == null ? "?" : selectedRoute.getName()), columnWidth()), right(), 53, 0xFFFFFFFF);
        graphics.text(font, shorten(endpoints(selected), columnWidth()), right(), 70, 0xFFFFFFFF);
        graphics.text(font, Component.translatable(selected.delivered()
                ? "screen.farandwide.order.delivered" : "screen.farandwide.order.outstanding"), right(), 87, 0xFFFFFFFF);
        int journeyY = 105;
        for (int index = 0; index < selected.legs().size(); index++) {
            OrderLeg leg = selected.legs().get(index);
            Route route = RouteManager.getRoute(leg.routeId());
            int received = leg.lines().stream().mapToInt((@NonNull OrderLine line) -> line.delivered()).sum();
            int requested = leg.lines().stream().mapToInt((@NonNull OrderLine line) -> line.requested()).sum();
            Component summary = Component.literal("Leg " + (index + 1) + "/" + selected.legs().size() + " · "
                    + (route == null ? "?" : route.getName()) + " · " + received + "/" + requested);
            graphics.text(font, shorten(summary, columnWidth()), right(), journeyY,
                    received == requested ? 0xFF55FF77 : 0xFFAAAAAA);
            journeyY += font.lineHeight;
        }
        Component warning = routeWarning(selected);
        if (warning != null) {
            List<net.minecraft.util.FormattedCharSequence> warningLines = font.split(warning, columnWidth());
            int warningY = journeyY + 5;
            for (int index = 0; index < Math.min(2, warningLines.size()); index++) {
                graphics.text(font, warningLines.get(index), right(), warningY, 0xFFFFAA00);
                warningY += font.lineHeight;
            }
            if (mouseX >= right() && mouseX < right() + columnWidth()
                    && mouseY >= journeyY + 3 && mouseY < warningY + 2) {
                graphics.setComponentTooltipForNextFrame(font, List.of(warning), mouseX, mouseY);
            }
        }
        for (int row = 0; row < itemRows(); row++) {
            int index = itemPage * itemRows() + row;
            if (index >= selected.lines().size()) break;
            OrderLine line = selected.lines().get(index);
            String count = line.delivered() + "/" + line.requested();
            Component name = OrderScreenSupport.itemName(line.resource());
            int y = itemTop() + row * 20;
            graphics.text(font, shorten(name, columnWidth() - font.width(count) - 8), right(), y, 0xFFFFFFFF);
            graphics.text(font, count, right() + columnWidth() - font.width(count), y,
                    line.remaining() == 0 ? 0xFF55FF77 : 0xFFFFFFFF);
            if (mouseX >= right() && mouseX < right() + columnWidth() && mouseY >= y && mouseY < y + 18) {
                graphics.setComponentTooltipForNextFrame(font, List.of(name, Component.literal(count)), mouseX, mouseY);
            }
        }
    }

    private @Nullable Component routeWarning(CargoOrder order) {
        for (OrderLeg leg : order.legs()) {
            List<VehicleRouteAssignment> assignments = RouteManager.getVehicleAssignments(leg.routeId());
            if (assignments.isEmpty()) return Component.translatable("screen.farandwide.order.no_vehicle_assigned");
            if (assignments.stream().noneMatch(VehicleRouteAssignment::active)) {
                return Component.translatable("screen.farandwide.order.vehicles_inactive");
            }
            if (leg.activationResult() != RouteOperationResult.SUCCESS
                    && leg.activationResult() != RouteOperationResult.NO_ASSIGNMENT) {
                return Component.translatable(leg.activationResult().translationKey());
            }
        }
        return null;
    }

}
