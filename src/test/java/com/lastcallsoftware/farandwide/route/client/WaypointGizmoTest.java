package com.lastcallsoftware.farandwide.route.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WaypointGizmoTest {
    @Test
    void cargoMarkerLabelIsDistinctFromNormalMarkerLabel() {
        assertEquals("4", WaypointGizmo.markerLabel(4, false));
        assertEquals("C4", WaypointGizmo.markerLabel(4, true));
    }
}
