# Persistence Regression Checklist

Use a disposable test world and record failures before refactoring. Repeat this
checklist after every structural change that affects routes, assignments,
networking, or persistence.

## Test setup

- [ ] Start from a clean compile.
- [ ] Create a new single-player world.
- [ ] Note any existing failures in the Results section before changing code.

## Routes and waypoints

- [ ] Create a route and verify that it becomes the selected route.
- [ ] Rename the route and verify the new name in the management and editor screens.
- [ ] Create a second route and switch the selection between both routes.
- [ ] Add waypoints and verify that they appear in order and in the current dimension.
- [ ] Remove a nearby waypoint and verify that other waypoints remain unchanged.
- [ ] Toggle a position twice and verify that the first action adds and the second removes.
- [ ] Delete a route and verify that it disappears and cannot remain selected.

## Traversal and assignments

Run these checks once while walking and once while riding a supported vehicle.

- [ ] Assign a route and verify that navigation targets the nearest waypoint.
- [ ] Toggle the assignment inactive and active and verify navigation follows the state.
- [ ] For `ONE_WAY`, reach each waypoint and verify traversal stops at the end.
- [ ] For `LOOP`, reach the final waypoint and verify traversal wraps to the first.
- [ ] For `REVERSE`, reach both ends and verify direction reverses at each end.
- [ ] Verify a player assignment follows the player.
- [ ] Verify a vehicle assignment follows the vehicle rather than its rider.

## Persistence and world isolation

- [ ] Save and exit with routes, selections, and active assignments present.
- [ ] Reload the world and verify route fields, waypoint dimensions, selections,
      assignment targets, directions, traversal overrides, and active state.
- [ ] Switch to another world and verify that no route cache leaks from the first world.
- [ ] Return to the first world and verify its state is restored.
- [ ] Delete a route, save, and reload; verify its assignments and selections stay deleted.

## Multiplayer smoke checks

- [ ] Join a dedicated server and verify the client receives its route snapshot.
- [ ] With two clients connected, mutate a route and verify both route caches update.
- [ ] Verify assignment state is delivered to the player controlling the assignee.

## Results

Record the date, commit or working-tree identifier, game mode, and any failures
here. Do not reinterpret an existing failure as a refactoring regression.

- Baseline date: 2026-08-25
- Baseline state: persistence feature present in an uncommitted working tree
- Clean compile: passed (`gradlew clean compileJava --no-daemon`)
- Final automated pass: passed (`gradlew clean test --no-daemon`, nine tests)
- Manual pass: pending
- Known issues: none recorded yet
