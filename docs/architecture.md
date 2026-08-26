# Architecture

Far and Wide stores routes and navigation assignments on the server. The client
keeps read-only snapshots for screens, rendering, and navigation display. A UI
action is therefore a request, not a direct edit of permanent state.

```text
screens, commands, and keybindings
                |
                v
       RouteManager client API
                |
                v
         network payloads
                |
                v
 authoritative server operations
                |
                v
      FarAndWideSavedData
```

Authoritative operation orchestration lives in `RouteService`. `RouteNetwork`
handlers translate payload actions into service calls and synchronize the
returned route or assignment views. Collection mutation and dirty-state handling
remain in `FarAndWideSavedData`.

## State ownership

`FarAndWideSavedData` owns all permanent, world-scoped state:

- routes and their names, traversal types, and dimension-aware waypoints;
- assignments and their target index, direction, optional traversal override,
  and active state;
- the selected route for each persistent assignee ID;
- the next route and assignee ID allocators.

The client-side `RouteManager` owns only replaceable caches:

- route snapshots and the selected route snapshot;
- assignment snapshots keyed by the entity's runtime ID;
- request flags and a revision counter used by client views.

These caches are cleared when leaving or joining a server. They must never be
treated as save data or as proof that a server mutation succeeded.

## Identifiers

Route IDs are stable integers stored with route data. Assignee IDs are stable
integers attached to players or vehicles and used as persistence keys. They are
separate from runtime entity IDs, which may change after a reload. Snapshot
delivery translates a persistent assignment back to the current entity ID so
the client can find the entity it is rendering or controlling.

Loaded ID allocators are raised above the highest restored ID to prevent reuse.
Deleting a route also removes assignments and selected-route entries that refer
to it.

## Dirty-state rule

Call `setDirty()` after every successful mutation of saved state, including ID
allocation. Do not call it for rejected requests or reads. Mutating a `Route` or
`RouteAssignment` obtained from saved data without going through a saved-data
operation is unsafe because the mutation can be omitted from disk. The planned
service and domain boundaries exist to make that mistake difficult.

## Network flow

Client to server:

- `RequestRouteSnapshotPayload` asks for routes and the requesting player's selection.
- `SelectRoutePayload` changes the requesting player's selected route.
- `RouteMutationPayload` creates, updates, deletes, adds, removes, or toggles a waypoint.
- `RequestAssignmentSnapshotPayload` asks for the current player or ridden vehicle assignment.
- `AssignmentMutationPayload` assigns a route or toggles assignment activity.

Server to client:

- `RouteSnapshotPayload` replaces the client route cache. Route mutations are
  broadcast, while the initiating player also receives its selected route ID.
- `AssignmentSnapshotPayload` supplies an assignment using the assignee's
  current runtime entity ID. It is sent after assignment mutations and traversal
  progress changes.

Client-only payload handling is registered by `FarAndWideClient`; common packet
registration must not load client classes on a dedicated server.

## Joining and switching worlds

On login, `RouteManager` clears all cached routes and assignments and requests a
fresh route snapshot. Assignment data is requested lazily after the local
player has a runtime entity ID. Logout clears the same cache, preventing one
world or server from leaking state into another.

The server retrieves `FarAndWideSavedData` from its world data storage. Minecraft
decodes the saved-data codec when the world is loaded and encodes it after dirty
state is saved.

## Traversal progress

`ServerRouteTraversalController` scans loaded entities for persistent assignee
IDs. When an active assignee reaches its target in the target dimension, the
server advances the assignment:

- `ONE_WAY` advances until the last waypoint, then becomes inactive;
- `LOOP` wraps from the final waypoint to the first;
- `REVERSE` changes direction at either end.

Progress is written through `FarAndWideSavedData`, which marks it dirty, and the
updated assignment is sent to a controlling player. Client navigation renders
the snapshot but does not authoritatively advance it.

## Physical-side and package boundaries

Feature code should remain grouped under `route` or `vehicle`, with physical
side subpackages only where they convey a real loading boundary:

- common domain models live directly under `route`;
- Minecraft client imports live under `route.client`, `vehicle.client`, or the
  top-level physical-client bootstrap package;
- authoritative operations and traversal live under `route.server`;
- saved-data and disk codecs live under `route.persistence`;
- payloads and registration live under `route.network`.

Common and server code must never reference a client subpackage. Packet records
remain common because both physical sides encode or decode them. Package moves
must remain small enough to compile and smoke-test independently because event
registration and class loading are physical-side sensitive.

## Adding a persisted field

To add a permanent route or assignment value:

1. Add it to the common domain model.
2. Add it to the disk codec, including an explicit compatibility default or migration.
3. Add it to the corresponding snapshot codec.
4. Update authoritative editing operations and synchronization.
5. Add disk round-trip and snapshot round-trip characterization tests.
6. Increase the data version when old saves require migration rather than a default.

Temporary UI state belongs only in client code and must not be added to the disk codec.
