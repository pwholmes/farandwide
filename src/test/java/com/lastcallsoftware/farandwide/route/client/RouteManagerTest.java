package com.lastcallsoftware.farandwide.route.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.route.TraversalType;
import com.lastcallsoftware.farandwide.route.VehicleRouteAssignment;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RouteManagerTest {
    private static final Route SELECTED_ROUTE = new Route(1, "Selected", TraversalType.ONE_WAY, List.of());

    @SuppressWarnings("deprecation")
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (var item : List.of(Items.AIR, Items.GOLD_INGOT)) {
            item.builtInRegistryHolder().bindComponents(DataComponents.COMMON_ITEM_COMPONENTS);
        }
    }

    @Test
    void serverClearingSelectionAlsoClearsTheHudSelection() {
        RouteManager.replaceRoutesFromServer(List.of(SELECTED_ROUTE), SELECTED_ROUTE.getId());
        assertEquals(SELECTED_ROUTE.getId(), RouteManager.getCurrentRouteId());

        RouteManager.replaceRoutesFromServer(List.of(SELECTED_ROUTE), 0);

        assertNull(RouteManager.getCurrentRoute());
        assertEquals(0, RouteManager.getCurrentRouteId());
    }

    @Test
    void respawningCannotReuseTheOldPlayersActiveAssignment() {
        int runtimeEntityId = 101;
        RouteManager.replaceRoutesFromServer(List.of(SELECTED_ROUTE), SELECTED_ROUTE.getId());
        RouteManager.replaceAssignmentFromServer(runtimeEntityId, 7,
                new RouteAssignment(1, 7, 0, 1, null, true));

        RouteManager.onClientRespawn();

        assertNull(RouteManager.getCurrentRoute());
        assertNull(RouteManager.getAssignment(runtimeEntityId));
        assertEquals(SELECTED_ROUTE, RouteManager.getRoute(SELECTED_ROUTE.getId()));
    }

    @Test
    void changingWorldClearsOrderSnapshotsAndAcknowledgments() {
        var id = java.util.UUID.randomUUID();
        var order = new com.lastcallsoftware.farandwide.route.CargoOrder(id, java.util.UUID.randomUUID(), 1, 2, 3,
                new com.lastcallsoftware.farandwide.route.CargoStationBinding(net.minecraft.core.BlockPos.ZERO, net.minecraft.core.Direction.UP),
                List.of(new com.lastcallsoftware.farandwide.route.OrderLine(net.minecraft.resources.Identifier.parse("minecraft:gold_ingot"), 4, 2)),
                com.lastcallsoftware.farandwide.route.RouteOperationResult.SUCCESS);
        RouteManager.replaceOrdersFromServer(List.of(order));
        RouteManager.handleOrderReply(id, com.lastcallsoftware.farandwide.route.OrderResult.PLACED, "");

        RouteManager.clearClientState();

        assertTrue(RouteManager.getOrders().isEmpty());
        org.junit.jupiter.api.Assertions.assertNull(RouteManager.getOrderFeedback());
        assertFalse(RouteManager.isOrderRequestPending());
    }

    @Test
    void navigationDataRequiresTheSelectedRouteToBeAssigned() {
        assertTrue(RouteManager.isSelectedRouteAssigned(SELECTED_ROUTE, new RouteAssignment(1, 7, 0)));
        assertFalse(RouteManager.isSelectedRouteAssigned(SELECTED_ROUTE, new RouteAssignment(2, 7, 0)));
        assertFalse(RouteManager.isSelectedRouteAssigned(SELECTED_ROUTE, null));
        assertFalse(RouteManager.isSelectedRouteAssigned(null, new RouteAssignment(1, 7, 0)));
    }

    @Test
    void startingOneVehicleMakesRouteActiveWithoutStartingItsSiblings() {
        RouteManager.replaceVehicleAssignmentsFromServer(List.of(
                new VehicleRouteAssignment(10, 1, "Boat 1", 0, true),
                new VehicleRouteAssignment(11, 1, "Boat 2", 0, false),
                new VehicleRouteAssignment(12, 2, "Boat 3", 0, false)));

        assertTrue(RouteManager.isRouteActive(1));
        assertFalse(RouteManager.isRouteActive(2));
        assertTrue(RouteManager.getVehicleAssignments(1).get(0).active());
        assertFalse(RouteManager.getVehicleAssignments(1).get(1).active());
    }

    @Test
    void managedDisplayNameIsResolvedFromStableAssigneeIdentity() {
        int runtimeEntityId = 9001;
        int stableAssigneeId = 42;
        RouteManager.replaceAssignmentFromServer(
                runtimeEntityId, stableAssigneeId, new RouteAssignment(1, runtimeEntityId, 0));
        RouteManager.replaceVehicleAssignmentsFromServer(List.of(
                new VehicleRouteAssignment(stableAssigneeId, 1, "Boat 3", 0, false)));

        assertEquals("Boat 3", RouteManager.getManagedAssigneeDisplayName(runtimeEntityId));
    }
}
