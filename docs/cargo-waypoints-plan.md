# Cargo Waypoints Implementation Plan

## Goal

Add server-authoritative cargo transportation to auto-navigated vehicles. A
route may contain any number of cargo waypoints, allowing a vehicle to load or
unload items at stops anywhere along the route, independently of the route's
traversal type.

The initial cargo-capable vehicles are:

- donkeys equipped with a chest;
- mules equipped with a chest; and
- chest boats, subject to confirming their Minecraft 26.2 inventory API.

Ordinary horses and other supported vehicles continue to auto-navigate but do
not perform cargo transfers when they reach a cargo waypoint.

## Agreed product behavior

### Custom waypoint behavior

Cargo behavior is a property of an individual waypoint, not a property of a
route endpoint. A route can contain normal and cargo waypoints in any order.
Cargo waypoints work with every traversal type:

- `ONE_WAY` processes each cargo waypoint encountered before the route stops;
- `LOOP` processes cargo waypoints on every circuit; and
- `REVERSE` processes cargo waypoints in both travel directions.

The same cargo configuration applies in either direction during the first
release. Direction-specific behavior may be added later if a concrete use case
justifies the additional UI and state.

### Cargo operations

Each cargo waypoint supports one of these operations:

- **Load:** move matching items from station storage into the vehicle.
- **Unload:** move matching items from the vehicle into station storage.
- **Unload, then load:** perform both operations in that order.

The MVP completes every transfer that is immediately possible, then continues
the route. A full or missing inventory does not leave a vehicle waiting
indefinitely.

### Item selection

Exact item selection is desirable but not required for the first usable cargo
release. The data model should permit independent load and unload filters so
filtering can be added without redesigning waypoint persistence.

The initial filter modes, when implemented, are:

- all items; and
- an allow-list of item registry identifiers.

Load and unload filters are separate. This permits a stop to unload one set of
items and load another. Persist registry identifiers, not translated names or
raw numeric IDs.

### Placement and editing

The player has a distinct **Place Cargo Waypoint** action. Invoking it captures
the proposed waypoint position and opens the cargo configuration screen. The
waypoint is created on the server only when the player saves the dialog;
canceling creates nothing.

A placed cargo waypoint must remain editable in the MVP:

1. The player enables waypoint editing and aims at its world marker.
2. The targeted marker highlights.
3. The player invokes the context-sensitive **Edit Waypoint** action.
4. The cargo configuration screen opens with the current values.
5. Saving sends a server-authoritative update; canceling preserves the waypoint.

The same edit screen is used for creation and editing. It also allows conversion
between normal and cargo behavior. Converting a cargo waypoint to normal must
confirm that its cargo settings will be discarded.

An ordered waypoint-list UI is explicitly deferred to Phase 2. In-world marker
selection is the only cargo-waypoint editing entry point required for the MVP.

## Domain design

### Stable waypoint identity

Every waypoint needs a stable ID. List indices are unsafe mutation identifiers
because insertion, deletion, and reordering change them and a stale client could
otherwise update the wrong waypoint.

A representative model is:

```java
public record Waypoint(
        int id,
        Vec3 position,
        Identifier dimension,
        WaypointAction action) {
}
```

`WaypointAction` represents normal navigation or cargo behavior. This may be a
sealed hierarchy or an enum plus optional settings, depending on which produces
the clearest Mojang codecs and network codecs. The external behavior must not
depend on that implementation choice.

```java
public record CargoBehavior(
        CargoOperation operation,
        CargoFilter loadFilter,
        CargoFilter unloadFilter) {
}
```

Waypoint IDs should be allocated by `FarAndWideSavedData`, following the
existing route and assignee ID pattern. Requests that mutate a waypoint identify
it by `routeId` and `waypointId`.

Existing saved waypoints receive stable IDs during data restoration and default
to normal behavior. The migration must preserve route order, positions, and
dimensions.

### Authority and synchronization

Permanent waypoint data remains world-scoped and server-owned. The mutation
path is:

```text
screen or command
    -> RouteManager
    -> RouteRequests
    -> waypoint mutation payload
    -> RouteNetwork
    -> RouteService
    -> FarAndWideSavedData
    -> refreshed route snapshot
```

The server validates that the route and waypoint exist and that the cargo
configuration is valid. Successful replacements mark saved data dirty and
broadcast the updated route snapshot. Revision-based concurrent edit detection
is not part of the MVP.

Disk codecs must supply compatibility defaults for old saves. Wire codecs must
remain symmetric and retain collection and string bounds.

## Runtime cargo architecture

### Cargo vehicle adapters

Cargo inventory access is separate from movement actuation. Introduce a common
server-side abstraction that can answer whether an entity has usable cargo
storage and expose that inventory through a consistent item-handler interface.

Initial adapters cover chested donkeys, chested mules, and chest boats. Movement
support currently checks `AbstractHorse`, which already includes donkeys and
mules; rename or document horse-specific actuators and controls as equine
behavior and add explicit entity coverage tests.

An unsupported or unchested vehicle still follows its route. It skips the cargo
operation safely and may report a rate-limited diagnostic to a controlling
player.

### Station storage

For the MVP, a cargo waypoint resolves the compatible inventory directly below
its marker, searching downward through a small, documented fixed distance. This
provides a predictable station-building rule and avoids an arbitrary choice
among multiple adjacent containers.

Prefer NeoForge item-handler capabilities so compatible modded storage can work
alongside vanilla chests, barrels, and hoppers. A later version may allow the
player to bind a cargo waypoint to a specific storage block.

### Safe transfer rules

Cargo operations execute only on the authoritative server. Item movement must:

- respect slot validity, stack limits, and partial insertions/extractions;
- use simulate-then-execute behavior where the inventory API supports it;
- never discard the unaccepted remainder of a stack;
- mark inventories changed using their normal API; and
- bound work per tick if modded inventories could make an unbounded scan costly.

For **Unload, then load**, unloading always completes before loading begins. The
same item must not be unloaded and immediately loaded back during one visit;
the implementation must track the operation's original source contents or
otherwise exclude items inserted during the unload phase from the subsequent
load phase.

## Traversal integration

Cargo processing occurs after arrival detection and before the assignment
advances:

```text
arrive at target waypoint
    -> stop vehicle
    -> normal waypoint: no action
       cargo waypoint: resolve inventories and perform operation once
    -> advance assignment according to traversal type
    -> resume navigation on a later tick
```

The operation and assignment advancement should complete in the same server
tick for the MVP. This prevents the arrival-radius check from repeating a cargo
operation on successive ticks. If waiting conditions or animated transfer delays
are later introduced, add a persisted assignment phase such as `TRAVELING` and
`PROCESSING_WAYPOINT` before allowing multi-tick processing.

If station storage is missing, full, or incompatible, the vehicle skips the
transfer and continues. Cargo is never dropped or deleted as an error fallback.

## Delivery phases

### Phase 1: Cargo waypoint MVP

1. Add stable waypoint IDs and normal/cargo waypoint actions.
2. Migrate old waypoints to stable IDs with normal behavior.
3. Extend saved-data and route-snapshot codecs and their round-trip tests.
4. Add server-authoritative create, replace, convert, and delete operations by
   route ID and waypoint ID.
5. Add **Place Cargo Waypoint** and the reusable cargo configuration screen.
6. Add in-world marker targeting, highlighting, and **Edit Waypoint** behavior.
7. Visually distinguish cargo waypoint markers from normal markers.
8. Formalize auto-navigation support for horses, donkeys, and mules; expose
   cargo only for chested donkeys and mules.
9. Add chest-boat cargo support after confirming the 26.2 API.
10. Implement predictable station discovery and safe load, unload, and
    unload-then-load transfers.
11. Integrate cargo execution with waypoint arrival for all traversal types.
12. Update help text and user-facing terminology.

Unfiltered transfers may ship before allow-list filtering if necessary, but the
persistence model and screen layout should leave room for separate load and
unload filters.

### Phase 2: Management and richer logistics

- Add an ordered waypoint-list UI as a reliable editing fallback.
- Select, edit, delete, convert, and reorder waypoints from that list.
- Allow editing waypoints in another dimension without traveling to them.
- Add item allow-list selection if it did not ship in Phase 1.
- Consider direction-specific cargo behavior.
- Consider binding a waypoint to a specific storage block.
- Consider wait-until-full, wait-until-empty, timeout, or deactivate-on-failure
  policies with persisted processing state.

## Verification

Automated coverage should include:

- old-save restoration assigns waypoint IDs and normal behavior;
- disk and network round trips preserve cargo settings and filters;
- waypoint updates address stable IDs rather than mutable indices;
- full and partially full destinations neither duplicate nor lose items;
- invalid destination slots reject items without loss;
- unload-then-load does not immediately reload the items it just unloaded;
- normal horses and unchested equines skip cargo safely;
- chested donkeys, mules, and chest boats expose only their cargo slots;
- cargo actions execute exactly once per waypoint arrival;
- cargo waypoints behave under `ONE_WAY`, `LOOP`, and both directions of
  `REVERSE`; and
- missing or destroyed station storage does not block traversal or damage cargo.

Manual regression checks should cover creation cancellation, editing cancellation,
conversion confirmation, marker targeting when markers are close together,
save/reload persistence, dedicated-server behavior, and interaction with a
controlling rider.

## Out of scope for the MVP

- ordered waypoint-list editing;
- chunk loading or travel through unloaded chunks;
- multi-tick loading animations or delays;
- indefinite waiting for items or inventory space;
- direction-specific behavior;
- explicit storage-block binding; and
- route revision/conflict handling for simultaneous editors.
