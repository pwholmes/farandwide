package com.lastcallsoftware.farandwide.route;

import java.util.Objects;

/** Behavior performed once when a vehicle arrives at a waypoint. */
public sealed interface WaypointAction permits WaypointAction.Normal, WaypointAction.Cargo {
    Normal NORMAL = new Normal();

    static Normal normal() {
        return NORMAL;
    }

    static Cargo cargo(CargoBehavior behavior) {
        return new Cargo(behavior);
    }

    /** A navigation-only waypoint. */
    record Normal() implements WaypointAction {
    }

    /** A waypoint which performs the supplied cargo operation before traversal advances. */
    record Cargo(CargoBehavior behavior) implements WaypointAction {
        public Cargo {
            Objects.requireNonNull(behavior, "behavior");
        }
    }
}
