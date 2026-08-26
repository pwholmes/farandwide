# Extending Far and Wide

Use the narrowest layer that owns the behavior. Permanent state belongs on the
server; clients request changes and render snapshots.

## Adding a non-persistent client feature

Put route UI, HUD, or rendering behavior under `route.client`. Put vehicle input
behavior under `vehicle.client`. Client commands and keybindings belong under
`command.client`.

A client-only feature should usually change only its screen, renderer, command,
keybinding, or client configuration. It must not import persistence or call a
payload record directly. Use `RouteManager` for route state and actions.

Before finishing:

1. Confirm every class importing Minecraft client APIs is under a `client` package.
2. Confirm common and server packages do not import the new client class.
3. Join two different worlds and verify client state does not leak between them.

## Adding a new route operation

Follow the request path in order:

1. Add a readable method to `route.client.RouteManager`.
2. Add the corresponding transport method to `route.network.client.RouteRequests`.
3. Add or extend a payload under `route.network.payload`.
4. Register the payload and map it to one service call in `route.network.RouteNetwork`.
5. Implement validation and orchestration in `route.server.RouteService`.
6. Mutate permanent state through `route.persistence.FarAndWideSavedData` so the
   successful change is marked dirty.
7. Return a `RouteOperationResult` and add localized failure text when necessary.
8. Synchronize the changed snapshot.
9. Add a domain, persistence, or wire-codec characterization test.

Screens and commands must stop at `RouteManager`. Packet handlers should contain
only payload dispatch, service calls, and synchronization.

## Adding a persisted route or assignment field

1. Add the field to the immutable `Route` or `RouteAssignment` record.
2. Add it to the disk codec in `route.persistence.RouteCodecs`.
3. Decide how old saves obtain the field: use an explicit codec default for a
   compatible addition, or add migration and increment the data version.
4. Add it to `RouteSnapshotPayload` or `AssignmentSnapshotPayload`.
5. Update the relevant `FarAndWideSavedData` replacement operation.
6. Add editing and service support if players can change it.
7. Extend both disk round-trip and snapshot round-trip tests.
8. Test loading an old save and saving it again.

Do not add a setter to the domain record. Reconstruct the immutable value inside
`FarAndWideSavedData`; that keeps dirty-state handling on the authoritative path.

## Adding temporary assignment behavior

First decide who must agree on the value:

- Persisted across restarts or relevant to gameplay authority: server-owned and
  stored in `RouteAssignment`.
- Needed by the server only while an entity is loaded: server-owned runtime state,
  not part of the disk codec.
- Pure display or input state: client-owned under `route.client` or `vehicle.client`.
- Needed for authoritative behavior and rendering: server-owned with a client snapshot.

Document the choice next to the state. Never use a runtime entity ID as a disk
key; persistent assignments use attachment-backed assignee IDs, while snapshots
translate them to the current runtime entity ID.

## Changing serialization or networking

Disk and wire formats are separate contracts:

- Disk codecs live in `RouteCodecs` and must preserve existing worlds.
- Wire codecs live in payload records and must remain symmetric and bounded.
- `RouteRequests` hides packet construction from client behavior.
- `RouteNetwork` hides NeoForge registration and distribution from domain code.

Whenever either format changes, add a round-trip test and verify malformed input
is rejected or repaired without discarding unrelated valid records.

## Completion checklist

For any extension that touches permanent or synchronized state:

1. Run `gradlew test --no-daemon`.
2. Run a clean compile.
3. Exercise the relevant items in `docs/regression-checklist.md`.
4. Save, exit, reload, and verify the new state.
5. Switch worlds and confirm caches are isolated.
6. Test on a dedicated server when physical-side loading or networking changed.
