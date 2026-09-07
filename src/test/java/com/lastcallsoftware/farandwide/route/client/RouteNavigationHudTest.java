package com.lastcallsoftware.farandwide.route.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RouteNavigationHudTest {
    @Test
    void bullseyeAppearsInsideHorizontalArrivalRadius() {
        assertTrue(RouteNavigationHud.shouldShowBullseye(3.5, 3.5, false));
        assertFalse(RouteNavigationHud.shouldShowBullseye(3.51, 3.5, false));
    }

    @Test
    void visibleBullseyeUsesExitMarginToAvoidBoundaryFlicker() {
        assertTrue(RouteNavigationHud.shouldShowBullseye(3.75, 3.5, true));
        assertFalse(RouteNavigationHud.shouldShowBullseye(3.76, 3.5, true));
    }
}
