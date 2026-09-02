# Architecture

Far and Wide is server-authoritative. Routes, waypoints, assignments, traversal
progress, and cargo behavior are permanent world state. Clients send requests
and keep replaceable snapshots for screens, rendering, input, and the HUD.

```text
client UI/input
    -> RouteManager
    -> RouteRequests and network payloads
    -> RouteNetwork
    -> RouteService
    -> FarAndWideSavedData
    -> refreshed client snapshots
```

Screens, commands, renderers, and client vehicle controls use `RouteManager`;
they do not construct payloads or edit cached records directly. Packet handlers
decode requests, call the server service, and synchronize results. Validation
and orchestration belong in `RouteService`, while collection mutation and dirty
state handling belong in `FarAndWideSavedData`.

## State and identity

`FarAndWideSavedData` owns routes, waypoint definitions, assignments, player
route selections, stable ID allocators, and the persistent metadata needed to
restore vehicle assignments. Domain records are immutable so permanent changes
must replace stored values through saved-data operations.

The client-side `RouteManager` owns only connection-scoped caches. These caches
are cleared when joining or leaving a server and are never evidence that a
server mutation succeeded.

Route, waypoint, and assignee IDs are stable persistence identities. Runtime
entity IDs may change after reload and must never be used as disk keys. Network
snapshots translate persistent assignees to current runtime entities when the
client needs to locate them. Loaded ID allocators must advance beyond every
restored ID, and deleting a route must also remove references to it.

## Persistence and synchronization

Every successful saved-state mutation, including ID allocation, calls
`setDirty()`. Rejected operations and reads do not. Code must not mutate an
object obtained from saved data behind its owner's back.

Disk and wire formats are separate contracts:

- Disk codecs live in `route.persistence.RouteCodecs` and must preserve existing
  worlds through explicit defaults or migration.
- Network codecs live with their payloads and must be symmetric and bounded.
- Server snapshots replace client state; clients never infer authoritative state
  from an optimistic local edit.

When a permanent field changes, update its domain model, disk codec, relevant
snapshot or mutation payload, authoritative operation, and round-trip tests.
Increment the data version only when compatibility requires migration rather
than a safe default. Malformed input should be rejected or repaired without
discarding unrelated valid data.

Temporary state belongs at the narrowest owner that needs it:

- authoritative across restarts: persisted server state;
- authoritative only while running: server runtime state;
- display or input only: client state;
- authoritative and visible to clients: server state plus a snapshot.

## Traversal and vehicles

`ServerRouteTraversalController` advances active assignments when an assignee
reaches its target. Progress is persisted and synchronized. Clients may render
or apply synchronized control intent, but the server owns route selection,
assignment state, waypoint actions, and traversal advancement.

A `VehicleNavigator` converts the current segment into a side-neutral
`NavigationIntent`. A `VehicleActuator` translates that intent into a vehicle's
mechanics and declares which physical side owns movement. This keeps route
storage independent of vehicle controls: navigation strategies can change and
vehicle types can be added without redesigning the route model.

Cargo policy belongs to each cargo waypoint. Load and unload filters and station
bindings are independent because a station identifies inventory access, not a
shared route policy. Transfers execute authoritatively and must preserve items
across partial or rejected moves.

## Package and physical-side boundaries

Use packages as ownership boundaries, not as a requirement to add a new layer
for every feature:

- common domain models live directly under `route` or `vehicle`;
- Minecraft client imports live under a `client` package or the client bootstrap;
- authoritative route behavior lives under `route.server`;
- saved-data and disk codecs live under `route.persistence`;
- payloads and network registration live under `route.network`.

Common and server code must not reference client packages. Packet records remain
common when both physical sides encode or decode them. Changes involving client
class loading, event registration, or networking require a dedicated-server
smoke test.

## Change checks

Test the contract affected by a change rather than following a fixed checklist
for every edit. Persisted or synchronized changes normally require disk and wire
round trips, an old-save compatibility case, save/reload verification, and a
world-switch cache-isolation check.
