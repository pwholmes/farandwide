# Cargo Item Filters Implementation Plan

## Decision

Item filters belong to each cargo waypoint, with independent filters for its
load and unload directions. A `CargoStationBinding` currently identifies only a
block inventory and access face; it has no stable identity or lifecycle of its
own. Putting transfer policy on that value would also couple unrelated routes
that happen to use the same inventory.

The existing domain model already reflects this decision:

```java
CargoBehavior(
    CargoOperation operation,
    CargoFilter loadFilter,
    CargoFilter unloadFilter,
    Optional<CargoStationBinding> loadStation,
    Optional<CargoStationBinding> unloadStation)
```

The persistence codecs, route snapshot and mutation payloads, and server-side
transfer loop already carry and apply both filters. The remaining work is
domain hardening, the player-facing editor, and end-to-end verification.

## Product behavior

- Each active transfer direction offers **All items** or **Selected items
  only**.
- Load and unload selections are independent.
- The selected-items mode must contain at least one item. This avoids silently
  saving a transfer direction that can never do work.
- Players select items from a searchable registry browser, not only from their
  current inventory.
- Unknown registry identifiers loaded from a save are retained and displayed by
  identifier. This lets route configuration survive a temporarily removed mod.
- Changing the cargo operation hides irrelevant controls but does not erase
  their in-progress values.
- Canceling a filter editor or the waypoint editor leaves the saved waypoint
  unchanged.

## Slice 1: Domain validation and tests

Status: Implemented.

1. Make `CargoFilter` the single source of truth for filter invariants:
   reject nulls, canonicalize duplicate identifiers while retaining order,
   require coherent mode/list combinations, and enforce the network item limit.
2. Add query helpers so callers do not duplicate matching semantics.
3. Use the helper from the runtime item-transfer path.
4. Add focused unit tests for all-items behavior, allow-list matching,
   canonicalization, invalid combinations, nulls, and size limits.
5. Retain the existing disk and wire representation; no save-data migration is
   required.

## Slice 2: Filter editor UI

Status: Implemented.

1. Add a reusable `CargoFilterScreen` that receives a current immutable filter,
   edits a local copy, and returns it only when **Done** is pressed.
2. Add a UI-independent editor-state class for mode changes, item toggling,
   search, scrolling, validation, and cancellation tests.
3. Populate the browser from the synchronized client item registry. Exclude
   air/placeholder entries, sort by translated name with registry ID as a stable
   tie-breaker, and search both translated names and identifiers.
4. Render item icons in a scrollable grid with selected state and tooltips that
   include translated name and registry ID.
5. Display unknown saved identifiers separately by raw ID so they can be kept
   or removed.
6. Add **Edit Load Filter...** and **Edit Unload Filter...** controls to
   `CargoWaypointScreen`, visible only for directions used by the current cargo
   operation.
7. Replace placeholder filter labels with summaries such as `All items` or
   `Coal, Iron Ingot +3` and reflow the screen for narrow/small resolutions.

## Slice 3: Integration, copy, and regression coverage

Status: Implemented, except for player help copy, which is intentionally left
for the project owner to update.

1. Add translations for filter modes, editor actions, search, summaries,
   tooltips, and validation messages.
2. Replace the help-page statement that filters are not implemented with usage
   instructions.
3. Extend disk and network round-trip tests for multiple items and limit
   boundaries.
4. Cover independent filtering for load, unload, and unload-then-load behavior,
   including timed transfers.
5. Verify editing preserves filters when changing unrelated waypoint fields.
6. Manually verify single-player and dedicated-server behavior, save/reload,
   partial and full inventories, modded items, missing-mod identifiers, and
   cancel flows.

## Acceptance criteria

- A player can configure different item allow-lists for loading and unloading.
- Only matching items move, without duplication or loss.
- Filters survive waypoint editing, network synchronization, and world reloads.
- Invalid or oversized filters cannot enter permanent server state.
- Existing unfiltered cargo waypoints continue to behave as before.
- No persistence migration is needed for the feature.
