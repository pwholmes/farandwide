# Far And Wide

**Far And Wide** lets you record routes and automate repeat travel and cargo transport in Minecraft.

Create a route, add waypoints while travelling it manually, assign the route to a vehicle, and activate it. The vehicle will then follow the route automatically.

## Requirements

- Minecraft 26.2
- NeoForge 26.2.0.66
- Far And Wide 1.1.0

Install the JAR in the instance’s `mods` folder. For multiplayer, install it on both the client and server.

## Supported Vehicles

- Boats
- Horses
- Donkeys
- Mules
- The player

Cargo transport is supported by:

- Chest boats
- Donkeys and mules equipped with chests

## Quick start

1. Open the Far And Wide command menu (default: `Ctrl + F`).
2. Create a Route and choose its traversal type:
   - **One Way** — stops at the final waypoint.
   - **Loop** — returns to the first waypoint after the last.
   - **Reverse** — reverses direction at each end.
3. Travel the intended Route manually, using **Add or Remove Waypoint** (default: `K`) to add Waypoints.
4. Mount a supported Vehicle and use **Assign Route** (default: `Ctrl + A`)
5. Use **Toggle Route** (default: `Ctrl + R`) to start or pause it.
6. You may dismount the Vehicle at any time and it will keep moving along the Route.

All controls are configurable in Minecraft’s Controls menu under the **Far And Wide** heading.

If you want to minimize the number of mapped keys the mod uses to avoid conflicts with other mods, all you really need is a mapping for the Far And Wide command menu -- everything else can be accessed from there.

## Cargo routes

Edit any Waypoint by 'using' it (default: right-click) and change it into a Cargo Waypoint.  Configure it to load items, unload items, or both, and connect nearby inventories as 'Stations'.

Item filters can restrict which items a Vehicle loads or unloads.  Cargo Stations can use compatible inventory blocks such as chests, barrels, hoppers, and furnaces.

Each stack loaded or unloaded takes one second at normal server speed. There is no fixed wait when nothing can transfer. Resuming a completed One Way Route returns the Vehicle to its last endpoint and performs that Waypoint's cargo operation again before beginning the return journey.

## Orders

Edit the origin Cargo Waypoint, select **Sources**, and link the inventories that supply its Load Station. Sources may be up to 8 blocks from the Waypoint. Press **Done**, then **Save** in the Waypoint editor. The Load Station must be separate from the Source Inventories.

Open **Manage Orders** from the command menu and choose **Place Order**. Select a Route and the numbered origin/destination Waypoints, then move currently available items from the left inventory into the Order Items inventory on the right. Both endpoint filters must allow those items. Named, enchanted, damaged, and other modified item variants are shown and ordered separately so their components remain intact.

An Order is accepted only when all requested items can be moved from the linked sources into the Load Station at once. Insufficient stock or space leaves every inventory unchanged. The Route is then activated; providing suitable Vehicles and arranging enough trips remains the player's responsibility. Remote assembly loads only the linked storage chunks and requires server chunk loading to be enabled when those chunks are not already loaded.

Tracking counts actual unloads at the selected destination, crediting the oldest matching outstanding Order first. Existing chest contents and manual deposits do not count; collecting delivered items does not erase progress. Multiple Vehicles and trips may fulfill one Order. Orders are visible to their requesting player and persist across saves. **Remove Tracking** cancels only the record, leaving cargo and Route activity unchanged. Deleting a Route or an Order's endpoint also removes its tracking record.

## Important route-safety notes

Vehicles navigate directly toward their next waypoint —- they do not pathfind around obstacles.  Make sure your Routes avoid walls, lava, and other hazards.

Routes can also keep nearby chunks loaded while an assigned Vehicle is travelling.  Server operators can configure the chunk-loading radius around a Vehicle and maximum number of chunk-loaded vehicles.

## Help and feedback

Use the in-game **Far And Wide Help** screen from the command menu for guided instructions.

Please report bugs and feature requests through the project’s issue tracker.

## License

The mod uses the MIT license, which is fully specified in LICENSE.txt.
