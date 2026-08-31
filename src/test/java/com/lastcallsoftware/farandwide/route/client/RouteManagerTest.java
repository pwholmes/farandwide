package com.lastcallsoftware.farandwide.route.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lastcallsoftware.farandwide.route.Route;
import com.lastcallsoftware.farandwide.route.RouteAssignment;
import com.lastcallsoftware.farandwide.route.TraversalType;
import com.lastcallsoftware.farandwide.route.VehicleRouteAssignment;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteManagerTest {
    private static final Route SELECTED_ROUTE = new Route(1, "Selected", TraversalType.ONE_WAY, List.of());

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
}
