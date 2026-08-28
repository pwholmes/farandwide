package com.lastcallsoftware.farandwide.route.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.lastcallsoftware.farandwide.route.Waypoint;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class WaypointEditorTest {
    private static final Identifier OVERWORLD = Identifier.parse("minecraft:overworld");
    private static final Identifier NETHER = Identifier.parse("minecraft:the_nether");

    @Test
    void aimingThroughCloseMarkersSelectsTheNearestIntersection() {
        Waypoint near = new Waypoint(10, new Vec3(0, 0.1, 4), OVERWORLD,
                com.lastcallsoftware.farandwide.route.WaypointAction.normal());
        Waypoint far = new Waypoint(20, new Vec3(0, 0.1, 8), OVERWORLD,
                com.lastcallsoftware.farandwide.route.WaypointAction.normal());

        Waypoint selected = WaypointEditor.selectTarget(
                List.of(far, near), OVERWORLD, new Vec3(0, 1.6, 0), new Vec3(0, 0, 1), 16, 0.7);

        assertEquals(near, selected);
    }

    @Test
    void targetingIgnoresOtherDimensionsAndMarkersOutsideAimRadius() {
        Waypoint offAxis = new Waypoint(10, new Vec3(2, 0.1, 4), OVERWORLD,
                com.lastcallsoftware.farandwide.route.WaypointAction.normal());
        Waypoint otherDimension = new Waypoint(20, new Vec3(0, 0.1, 4), NETHER,
                com.lastcallsoftware.farandwide.route.WaypointAction.normal());

        assertNull(WaypointEditor.selectTarget(
                List.of(offAxis, otherDimension), OVERWORLD,
                new Vec3(0, 1.6, 0), new Vec3(0, 0, 1), 16, 0.7));
    }
}
