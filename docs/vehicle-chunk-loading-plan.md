# Vehicle chunk loading plan

## Goal

Keep every supported vehicle with an active route assignment loaded and ticking,
including when no player is nearby. Chunk loading must follow the vehicle rather
than force-loading the complete route.

The current traversal controller discovers assignees by scanning loaded entities.
Once a vehicle's chunk unloads, the vehicle disappears from that scan and cannot
request its own chunk again. Chunk tickets therefore have to be acquired while
the vehicle is still loaded and persisted by NeoForge across server restarts.

## Design

Use one NeoForge `TicketController` owned by Far and Wide. Each active supported
vehicle owns a square ticket window around its current chunk. The default radius
is one (a maximum of nine chunks per vehicle), and servers can configure it from
zero through four.

When the vehicle changes chunks:

1. Compute the desired window around its new chunk.
2. Add every newly required ticket.
3. Remove tickets that are no longer in the window.

Adding first prevents a vehicle at a chunk boundary from briefly losing its
simulation area. Entity UUIDs are ticket owners because they remain stable when
an entity unloads and NeoForge persists entity-owned forced-chunk tickets.
Natural spawning is not enabled by these tickets.

Tickets are released when an assignment is deactivated or removed, a one-way
route completes, its route becomes invalid or is deleted, or the entity ceases
to be a supported vehicle. Server shutdown clears only the in-memory window
cache; NeoForge's persisted tickets remain so the vehicle can bootstrap itself
on the next load.

## Implementation phases

### Phase 1: moving ticket window

- [x] Register the Far and Wide vehicle ticket controller on the mod event bus.
- [x] Add a server-owned vehicle chunk-loading manager.
- [x] Acquire and move a 3x3 ticket window before navigation is applied.
- [x] Release runtime tickets on normal stop, unassignment, route deletion, and
  traversal completion paths.
- [x] Clear the in-memory window cache when the server stops without deleting
  persisted tickets.
- [ ] Add game tests that move a vehicle across a chunk boundary without a
  nearby player and assert that traversal continues.

### Phase 2: persisted-ticket validation

NeoForge invokes a validation callback before restoring persisted tickets.
Far and Wide persists assignments by integer assignee ID while chunk tickets
are owned by entity UUID, so the implementation also persists a bridge between
those identities. This lets startup validation prove that a saved ticket still
belongs to an active assignment before loading the entity.

- [x] Persist an assignee-ID-to-entity-UUID association for vehicle assignees.
- [x] Register a ticket loading-validation callback.
- [x] Remove saved tickets for missing, inactive, or deleted
  assignees before the chunks are activated.
- [x] Adopt validated saved ticket sets into the runtime cache so stale window
  edges can be removed immediately after load.
- [x] Clean up the UUID association when an entity is permanently destroyed,
  while distinguishing destruction from ordinary chunk unload and dimension
  transfer.
- [x] Add saved-data round-trip and assignment cleanup tests for vehicle UUIDs.
- [ ] Add an integration test for NeoForge ticket restoration and stale-ticket
  cleanup.

### Phase 3: limits and diagnostics

- [x] Add a server configuration for ticket radius and the maximum number of
  simultaneously chunk-loaded vehicles.
- [x] Define behavior when the limit is reached (pause the assignment and report
  a visible reason rather than silently unloading it).
- [x] Add debug logging that reports aggregate ticket-window usage.
- [ ] Measure server cost with overlapping routes and many vehicles.

## Acceptance criteria

- An active unattended vehicle crosses chunk boundaries and continues toward
  its waypoint.
- At every window move, new chunks are ticketed before old chunks are released.
- Restarting the server with an active vehicle away from all players loads that
  vehicle and resumes traversal.
- Deactivating or deleting a route releases all of its vehicle tickets.
- Completed one-way assignments do not retain tickets.
- Invalid or destroyed assignees cannot leave permanent forced chunks after
  Phase 2 is complete.

## Performance notes

The default radius-one window costs at most nine forced chunks per vehicle,
before overlap between nearby vehicles is considered. Radius zero costs one,
while the maximum radius four costs 81. This is deliberately independent of
route length. Route-wide chunk loading would scale with recorded distance and
could keep thousands of unused chunks ticking.
