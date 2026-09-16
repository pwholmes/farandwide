package com.lastcallsoftware.farandwide.route.server;

import com.lastcallsoftware.farandwide.route.*;
import com.lastcallsoftware.farandwide.route.persistence.FarAndWideSavedData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {
    private static ItemResource gold;
    private static ItemResource redstone;

    @SuppressWarnings("deprecation")
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Unit tests have no resource reload; these plain items need only the common stack components.
        for (var item : List.of(Items.AIR, Items.GOLD_INGOT, Items.REDSTONE)) {
            item.builtInRegistryHolder().bindComponents(DataComponents.COMMON_ITEM_COMPONENTS);
        }
        gold = ItemResource.of(Items.GOLD_INGOT);
        redstone = ItemResource.of(Items.REDSTONE);
    }

    @Test void assemblesExactQuantitiesAcrossSources() {
        var first = stock(gold, 3);
        var second = stock(gold, 10);
        var third = stock(redstone, 12);
        var destination = new ItemStacksResourceHandler(2);
        var result = OrderService.assemble(List.of(first, second, third), destination, request());
        assertEquals(OrderResult.PLACED, result.result());
        assertEquals(0, count(first, gold));
        assertEquals(9, count(second, gold));
        assertEquals(8, count(third, redstone));
        assertEquals(4, count(destination, gold));
        assertEquals(4, count(destination, redstone));
    }

    @Test void shortageRollsBackPreviouslyExtractedItems() {
        var source = stock(gold, 8);
        var destination = new ItemStacksResourceHandler(2);
        var result = OrderService.assemble(List.of(source), destination, request());
        assertEquals(OrderResult.INSUFFICIENT_STOCK, result.result());
        assertEquals(8, count(source, gold));
        assertEquals(0, count(destination, gold));
    }

    @Test void fullLoadStationRollsBackBothExtractionAndPartialInsertion() {
        var first = stock(gold, 8);
        var second = stock(redstone, 8);
        var destination = new ItemStacksResourceHandler(1);
        var result = OrderService.assemble(List.of(first, second), destination, request());
        assertEquals(OrderResult.LOAD_STATION_FULL, result.result());
        assertEquals(8, count(first, gold));
        assertEquals(8, count(second, redstone));
        assertEquals(0, count(destination, gold));
    }

    @Test void repeatedSourceAccessDoesNotDoubleCountStock() {
        var source = stock(gold, 3);
        var destination = new ItemStacksResourceHandler(2);
        assertEquals(OrderResult.INSUFFICIENT_STOCK,
                OrderService.assemble(List.of(source, source), destination, Map.of(gold, 4)).result());
        assertEquals(3, count(source, gold));
        assertEquals(0, count(destination, gold));
    }

    @Test void modifiedItemsAreNotSubstituted() {
        var named = Items.GOLD_INGOT.getDefaultInstance();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Keep me"));
        var resource = ItemResource.of(named);
        var source = stock(resource, 8);
        assertEquals(OrderResult.INSUFFICIENT_STOCK,
                OrderService.assemble(List.of(source), new ItemStacksResourceHandler(1), Map.of(gold, 4)).result());
        assertEquals(8, count(source, resource));
    }

    @Test void modifiedItemsCanBeOrderedWithTheirComponentsIntact() {
        var named = Items.GOLD_INGOT.getDefaultInstance();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Deliver me"));
        var resource = ItemResource.of(named);
        var source = stock(resource, 8);
        var destination = new ItemStacksResourceHandler(1);

        assertEquals(OrderResult.PLACED,
                OrderService.assemble(List.of(source), destination, Map.of(resource, 4)).result());
        assertEquals(4, count(source, resource));
        assertEquals(4, count(destination, resource));
        assertEquals(0, count(destination, gold));
    }

    @Test void retryDoesNotStageOrActivateTwiceAndActivationFailureRetainsOrder() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = data.createRoute();
        data.setTraversalType(route.getId(), TraversalType.LOOP);
        data.addWaypoint(route.getId(), new Waypoint(Vec3.ZERO));
        data.addWaypoint(route.getId(), new Waypoint(new Vec3(10, 0, 0)));
        List<Waypoint> waypoints = data.getRoute(route.getId()).getWaypoints();
        CargoOrder order = new CargoOrder(UUID.randomUUID(), UUID.randomUUID(), route.getId(),
                waypoints.getFirst().id(), waypoints.getLast().id(), new CargoStationBinding(BlockPos.ZERO, Direction.UP),
                List.of(new OrderLine(Identifier.parse("minecraft:gold_ingot"), 4, 0)), RouteOperationResult.SUCCESS);
        var source = stock(gold, 16);
        var destination = new ItemStacksResourceHandler(1);
        AtomicInteger activated = new AtomicInteger();
        for (int attempt = 0; attempt < 2; attempt++) {
            assertEquals(OrderResult.PLACED, OrderService.fulfill(data, order, List.of(source), destination, Map.of(gold, 4), () -> {
                activated.incrementAndGet();
                return RouteOperationResult.NO_ASSIGNMENT;
            }).result());
            // A retry must still acknowledge the original order if its route has since changed.
            data.setTraversalType(route.getId(), TraversalType.ONE_WAY);
        }
        assertEquals(12, count(source, gold));
        assertEquals(4, count(destination, gold));
        assertEquals(1, activated.get());
        assertEquals(RouteOperationResult.NO_ASSIGNMENT, data.getOrder(order.id()).activationResult());
    }

    @Test void rejectsOneWayOrdersBeforeMovingStockOrActivatingVehicles() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        Route route = data.createRoute();
        data.addWaypoint(route.id(), new Waypoint(Vec3.ZERO));
        data.addWaypoint(route.id(), new Waypoint(new Vec3(10, 0, 0)));
        List<Waypoint> waypoints = data.getRoute(route.id()).waypoints();
        CargoOrder order = new CargoOrder(UUID.randomUUID(), UUID.randomUUID(), route.id(),
                waypoints.getFirst().id(), waypoints.getLast().id(), new CargoStationBinding(BlockPos.ZERO, Direction.UP),
                List.of(new OrderLine(gold, 4, 0)), RouteOperationResult.SUCCESS);
        var source = stock(gold, 16);
        var destination = new ItemStacksResourceHandler(1);
        AtomicInteger activated = new AtomicInteger();
        assertEquals(OrderResult.UNSUPPORTED_TRAVERSAL, OrderService.fulfill(data, order, List.of(source), destination,
                Map.of(gold, 4), () -> {
                    activated.incrementAndGet();
                    return RouteOperationResult.SUCCESS;
                }).result());
        assertEquals(16, count(source, gold));
        assertEquals(0, count(destination, gold));
        assertEquals(0, activated.get());
        assertNull(data.getOrder(order.id()));
    }

    @Test void serverRejectsOneWayTraversalAtEveryPositionInAMultilegJourney() {
        FarAndWideSavedData data = new FarAndWideSavedData();
        List<Route> routes = List.of(data.createRoute(), data.createRoute(), data.createRoute());
        OrderJourney journey = new OrderJourney(routes.stream().map(route -> new OrderJourney.Leg(route.id(), 1, 2)).toList());
        routes.forEach(route -> data.setTraversalType(route.id(), TraversalType.LOOP));
        assertEquals(OrderResult.PLACED, OrderService.validateJourneyRoutes(data, journey));
        for (Route route : routes) {
            data.setTraversalType(route.id(), TraversalType.ONE_WAY);
            assertEquals(OrderResult.UNSUPPORTED_TRAVERSAL, OrderService.validateJourneyRoutes(data, journey));
            data.setTraversalType(route.id(), TraversalType.REVERSE);
            assertEquals(OrderResult.PLACED, OrderService.validateJourneyRoutes(data, journey));
        }
    }

    @Test void onlySuccessfulUnloadsProduceReceipts() {
        var source = stock(gold, 8);
        var full = stock(gold, 64);
        AtomicInteger credited = new AtomicInteger();
        assertEquals(0, CargoTransferService.transferOneStack(source, full, item -> true,
                (item, amount) -> credited.addAndGet(amount)));
        assertEquals(0, credited.get());
        var partial = stock(gold, 62);
        assertEquals(2, CargoTransferService.transferOneStack(source, partial, item -> true,
                (item, amount) -> credited.addAndGet(amount)));
        assertEquals(2, credited.get());
        assertEquals(6, count(source, gold));
    }

    @Test void sourceRangeIsEightBlocks() {
        double radius = com.lastcallsoftware.farandwide.Constants.Orders.SOURCE_RADIUS;
        assertEquals(8.0, radius);
        assertTrue(WaypointProximity.isWithinArrivalRadius(Vec3.ZERO, radius, new BlockPos(7, 0, 0)));
        assertFalse(WaypointProximity.isWithinArrivalRadius(Vec3.ZERO, radius, new BlockPos(9, 0, 0)));
    }

    @Test void rejectsDuplicateItemsAndClientSuppliedDeliveryCredit() {
        var line = new OrderLine(Identifier.parse("minecraft:gold_ingot"), 4, 0);
        assertFalse(OrderService.validRequest(List.of(line, line)));
        assertFalse(OrderService.validRequest(List.of(new OrderLine(line.itemId(), 4, 1))));
        assertFalse(OrderService.validRequest(List.of()));

        var named = Items.GOLD_INGOT.getDefaultInstance();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Different variant"));
        assertTrue(OrderService.validRequest(List.of(line, new OrderLine(ItemResource.of(named), 1, 0))));
    }

    private static Map<ItemResource, Integer> request() {
        Map<ItemResource, Integer> request = new LinkedHashMap<>();
        request.put(gold, 4);
        request.put(redstone, 4);
        return request;
    }

    private static ItemStacksResourceHandler stock(ItemResource resource, int count) {
        var handler = new ItemStacksResourceHandler(1);
        try (var transaction = Transaction.openRoot()) {
            assertEquals(count, handler.insert(resource, count, transaction));
            transaction.commit();
        }
        return handler;
    }

    private static int count(ItemStacksResourceHandler handler, ItemResource resource) {
        int total = 0;
        for (int slot = 0; slot < handler.size(); slot++) if (handler.getResource(slot).equals(resource)) total += handler.getAmountAsInt(slot);
        return total;
    }
}
