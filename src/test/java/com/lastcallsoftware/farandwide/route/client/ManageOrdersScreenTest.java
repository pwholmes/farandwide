package com.lastcallsoftware.farandwide.route.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.lastcallsoftware.farandwide.route.CargoOrder;
import com.lastcallsoftware.farandwide.route.CargoStationBinding;
import com.lastcallsoftware.farandwide.route.OrderLeg;
import com.lastcallsoftware.farandwide.route.OrderLine;
import com.lastcallsoftware.farandwide.route.RouteOperationResult;
import com.lastcallsoftware.farandwide.route.VehicleRouteAssignment;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ManageOrdersScreenTest {
    @SuppressWarnings("deprecation")
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Items.GOLD_INGOT.builtInRegistryHolder().bindComponents(DataComponents.COMMON_ITEM_COMPONENTS);
    }

    @Test
    void completedFirstLegDoesNotWarnAboutItsInactiveVehicle() {
        RouteManager.replaceVehicleAssignmentsFromServer(List.of(
                new VehicleRouteAssignment(10, 1, "First", 0, false),
                new VehicleRouteAssignment(11, 2, "Second", 0, true)));

        assertNull(ManageOrdersScreen.routeWarning(order(4, 1)));
    }

    @Test
    void completedOrderDoesNotWarnAboutInactiveVehicles() {
        RouteManager.replaceVehicleAssignmentsFromServer(List.of());

        assertNull(ManageOrdersScreen.routeWarning(order(4, 4)));
    }

    @Test
    void outstandingLegStillWarnsWhenItsVehicleIsInactive() {
        RouteManager.replaceVehicleAssignmentsFromServer(List.of(
                new VehicleRouteAssignment(10, 1, "First", 0, false),
                new VehicleRouteAssignment(11, 2, "Second", 0, false)));

        assertNotNull(ManageOrdersScreen.routeWarning(order(4, 1)));
    }

    private static CargoOrder order(int firstDelivered, int secondDelivered) {
        CargoStationBinding station = new CargoStationBinding(BlockPos.ZERO, Direction.UP);
        return new CargoOrder(UUID.randomUUID(), UUID.randomUUID(), List.of(
                new OrderLeg(1, 11, 12, station, List.of(line(firstDelivered)), RouteOperationResult.SUCCESS),
                new OrderLeg(2, 21, 22, station, List.of(line(secondDelivered)), RouteOperationResult.SUCCESS)));
    }

    private static OrderLine line(int delivered) {
        return new OrderLine(Identifier.parse("minecraft:gold_ingot"), 4, delivered);
    }
}
