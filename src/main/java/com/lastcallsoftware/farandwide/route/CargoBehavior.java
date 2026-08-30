package com.lastcallsoftware.farandwide.route;

import java.util.Objects;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNullByDefault;

/** Cargo settings with independent filters and station bindings for each transfer direction. */
@NonNullByDefault
public record CargoBehavior(CargoOperation operation, CargoFilter loadFilter, CargoFilter unloadFilter,
        Optional<CargoStationBinding> loadStation, Optional<CargoStationBinding> unloadStation) {
    public CargoBehavior {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(loadFilter, "loadFilter");
        Objects.requireNonNull(unloadFilter, "unloadFilter");
        loadStation = Objects.requireNonNull(loadStation, "loadStation");
        unloadStation = Objects.requireNonNull(unloadStation, "unloadStation");
    }

    /** Compatibility constructor for cargo settings created before station binding existed. */
    public CargoBehavior(CargoOperation operation, CargoFilter loadFilter, CargoFilter unloadFilter) {
        this(operation, loadFilter, unloadFilter, Optional.empty(), Optional.empty());
    }

    /** Compatibility constructor for settings that used one station in both directions. */
    public CargoBehavior(CargoOperation operation, CargoFilter loadFilter, CargoFilter unloadFilter,
            Optional<CargoStationBinding> station) {
        this(operation, loadFilter, unloadFilter, station, station);
    }

    public static CargoBehavior unfiltered(CargoOperation operation) {
        return new CargoBehavior(operation, CargoFilter.all(), CargoFilter.all());
    }

    public CargoBehavior withLoadStation(CargoStationBinding station) {
        return new CargoBehavior(operation, loadFilter, unloadFilter, Optional.of(station), unloadStation);
    }

    public CargoBehavior withUnloadStation(CargoStationBinding station) {
        return new CargoBehavior(operation, loadFilter, unloadFilter, loadStation, Optional.of(station));
    }

    /** A combined operation cannot use one inventory as both its source and destination. */
    public boolean usesSameStation() {
        return operation == CargoOperation.UNLOAD_THEN_LOAD
                && loadStation.flatMap(load -> unloadStation.map(unload -> load.position().equals(unload.position())))
                        .orElse(false);
    }

    /** Returns whether this behavior uses an inventory in the opposite direction from another behavior. */
    public boolean conflictsWithOppositeRole(CargoBehavior other) {
        return sharesStation(loadStationInUse(), other.unloadStationInUse())
                || sharesStation(unloadStationInUse(), other.loadStationInUse());
    }

    private Optional<CargoStationBinding> loadStationInUse() {
        return operation == CargoOperation.UNLOAD ? Optional.empty() : loadStation;
    }

    private Optional<CargoStationBinding> unloadStationInUse() {
        return operation == CargoOperation.LOAD ? Optional.empty() : unloadStation;
    }

    private static boolean sharesStation(Optional<CargoStationBinding> first, Optional<CargoStationBinding> second) {
        return first.flatMap(station -> second.map(other -> station.position().equals(other.position()))).orElse(false);
    }
}
