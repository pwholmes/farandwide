package com.lastcallsoftware.farandwide.route;

import java.util.Objects;

/** Behavior associated with a waypoint during arrival or portal crossing. */
public sealed interface WaypointAction permits WaypointAction.Normal, WaypointAction.Cargo, WaypointAction.Portal {
    Normal NORMAL = new Normal();
    Portal PORTAL = new Portal();

    static Normal normal() {
        return NORMAL;
    }

    static Cargo cargo(CargoBehavior behavior) {
        return new Cargo(behavior);
    }

    static Portal portal() {
        return PORTAL;
    }

    /** A navigation-only waypoint. */
    record Normal() implements WaypointAction {
    }

    /** One end of a crossing to an adjacent portal waypoint in another dimension. */
    record Portal() implements WaypointAction {
    }

    /** A waypoint which performs the supplied cargo operation before traversal advances. */
    record Cargo(CargoBehavior behavior) implements WaypointAction {
        public Cargo {
            Objects.requireNonNull(behavior, "behavior");
        }
    }
}
