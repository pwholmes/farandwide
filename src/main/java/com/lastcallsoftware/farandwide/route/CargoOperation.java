package com.lastcallsoftware.farandwide.route;

/** Transfer order requested by a cargo waypoint. */
public enum CargoOperation {
    LOAD(false, true),
    UNLOAD(true, false),
    UNLOAD_THEN_LOAD(true, true);

    private final boolean unloads;
    private final boolean loads;

    CargoOperation(boolean unloads, boolean loads) {
        this.unloads = unloads;
        this.loads = loads;
    }

    public boolean unloads() {
        return unloads;
    }

    public boolean loads() {
        return loads;
    }
}
