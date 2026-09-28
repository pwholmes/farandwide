1.0.0
-----
Initial revision.


1.1.0 - The Death Route release
-----
- Added: Added the "Death Route" feature.  When a player dies, a "(Player Name)'s Death Route" is automatically added (or replaced) with a single Waypoint at the location of the player's death.
- Added: Total Waypoints and total Route distance remaining were added to the HUD.
- Added: The directional arrow on the HUD changes to a bullseye target when within horizonal arrival distance of the next Waypoint.
- Added: A vertical direction indicator was added to the HUD.  A single chevron pointing up or down is shown for angles of less than 20 degrees elevation to the next Waypoint, double chevrons for higher angles.
- Added: Added an Invert Route command that inverts the order of a Route's Waypoints, such that the last Waypoint becomes Waypoint 1, the next-to-last Waypoint becomes Waypoint 2, etc.
- Added: Added a Select Route command to select the Route assigned to the current Vehicle.  Saves you the extra step of finding and selecting it in the Route Management screen.
- Changed: Far And Away screens are now non-pausing by default in single-player mode (they were already non-pausing in multiplayer).  This behavior can be toggled in the mod's config.  The Route Management screen has a 3-second refresh, so in non-pausing mode you can track the progress of Vehicles in real time as they progress from one Waypoint to the next.
- Changed: The Route Manager now automatically scrolls to a newly created Route.
- Changed: When the player has no selected Route and exits a Vehicle, the Vehicle's Route is no longer automatically selected.
- Changed: When mounting a Vehicle, the Vehicle's assigned Route is no longer automatically selected.  The old behavior can be enabled in the mod's config if desired.  Enable that if you prefer being able to view and edit a Vehicle's Waypoints when you mount it without having to issue any additional commands.
- Changed: The HUD no longer displays a Vehicle's generic name if it has a custom name (i.e., a name appied with a nametag).
- Fixed: The distances reported on HUD only calculated horizontal distance.  It now takes account of vertical distance as well.


1.2.0 - The Orders release
-----
- Added: Implemented the Order system, which allows players to request automatic delivery of items from remote storage.  This system was designed to integrate smoothly into normal gameplay, without sacrificing immersive role-playing.
- Added: The new Order sytem includes support for Multileg Orders spanning multiple Routes.  For example, a player can place an Order for items in their main warehouse, where a chest donkey will pick up and transport the items to a chest on a nearby dock, where they will be picked up by a boat on a second Route and shipped overseas to a chest at another dock, where another chest donkey on a third Route will pick them up and take them on the final leg of the journey to a chest where the player is waiting.
- Added: The walking speed of unmounted equines (horses, donkeys and mules) on Routes can now be set in the mod options.  The low end is the current unmounted speed and the high end is the current mounted speed, which is twice the normal unmounted rate.  The default speed has been increased to 1.5x.
- Changed: The Edit Waypoint screen was totally redesigned to be cleaner and more intuitive.
- Changed: Cargo Stations are now identified by block type (e.g., "chest") in addition to location.
- Fixed: If a player died while on an active Route, the Route remained active when the player respawned, resulting in them immediately moving toward the next Waypoint.  Dying now removes any selected or assigned Routes from the player.
- Fixed: In some cases the HUD would report a selected Route when there was none.  The selected Route is now cleared correctly.
- Fixed: The Route Management list auto-scrolled to the currently selected Route on every list refresh.  Auto-scrolling now only happens when the player creates a new Route.
- Fixed: Waypoint arrival radius was measured using a Vehicle's bounding box in some cases and from its center in other cases, with the result that the Vehicle could get stuck, stopping movement after it got close to a Waypoint, but not actually triggering Waypoint arrival and advancement to the next Waypoint.  Waypoint distance is now calculated consistently in all cases.
- Fixed: If a Cargo Waypoint's Load or Unload Station was broken or removed, the Waypoint was reported as still having that Station, and the Route continued in a broken state.  The Waypoint and Route are now adjusted appropriately and a message is broadcast to all players reporting the change.  (Routes are shared by all players so this is the appropriate message scope.)
- Fixed: If a Waypoint was deleted, Vehicles targeting that Waypoint as their next destination were not being properly updated.  Vehicles now target the next Waypoint on the Route or are deactivated if there are no such Waypoints.
- Fixed: Waypoint validation errors overlaid the instruction text for selecting load/unload stations.  The messages are now rendered in separate areas.
- Fixed: It's possible to delete a Waypoint while selecting its Load/Unload Station or Delivery Sources.  Previously the Waypoint edit would continue in this case, even though the Waypoint didn't exist anymore.  Deleting the Waypoint now cancels the edit action.

1.3.0 - The Minecart release
-----
- Added: Added support for minecarts!  You knew it had to happen eventually.  I resisted doing this because Minecraft already has some built-in support for automating minecarts and I didn't want to subvert or undermine that.  But I really wanted minecarts to participate in Far And Wide's logistics, and that impulse won.
- Added: Added the Round-Trip Route type.  It does just what the name implies: Vehicles on ths Route travel to the highest-numbered Waypoint, then return to Waypoint 1, then stop.  This Route type is particularly useful for Vehicles dedicated to Order delivery, as they will always be available at the start of the Route when idle.
- Added: Added the Portal Waypoint.  These are necessary to send vehicles between dimensions -- in particular, to the Nether.  Add a Portal Waypoint where the Route intersects the portal (i.e., in the portal itself) in each dimension (e.g., one in the Overworld and one in the Nether).  This manages Vehicle transitions between dimensions.  Think of it as two separate Routes, each of which terminates at a portal, stitched together.
- Changed: "Order Sources" are now known as "Source Inventories".
- Changed: The "Orders" screen is now known as "Manage Orders".
- Changed: The player no longer needs to be riding a Vehicle to assign it a Route or activate it -- the player can now also issue those commands to Vehicles they are looking at.  This was necessary because you can't mount chest minecarts, but it's an overdue change for other kinds of Vehicles too.
- Changed: Orders are now supported for One-way Routes.  When an Order is placed on a One-Way Route, all cargo-capable Vehicles on that Route are immediately activated, and it effectively becomes a Reverse Route until all outstanding Orders on it are completed.  The same behavior applies to the new Round-Trip Route type.
- Changed: The "Station radius", which determines how far a Station can be from its linked Waypoint, is now independent of the Waypoint's "arrival radius", which determines how close a Vehicle has to get to the Waypoint to decide it has reached it. The "Station radius" is now fixed at 8 blocks, and the arrival radius remains configurable in the Edit Waypoint screen.
- Changed: Items are now transferred in small "batches" at Cargo Waypoints, rather than a whole stack a time, with a small pause between each batch.  For starters, batch size is 4 items and the delay time is 0.25 seconds.
- Changed: Added a brief "dwell time" at Cargo Waypoints, and also between the stop and restart of a Reverse Route (so there's a double wait if the Waypoint is both).  For the feature's premiere, the dwell time is set to 2 seconds; that may be adjusted in the future.  The HUD shows a special icon ("zz's") while the Vehicle is in dwell mode so the user knows the Vehicle isn't just ignoring their commands.
- Changed: Waypoints are now placed exactly in the horizonal (x-z) center of their block.  Waypoints placed prior to this change are unaffected.
- Fixed: The backgrounds on many of the mod's screens were missing the semi-opaque dark background added for readability to most screens.  The screens are now all shaded consistently.
- Fixed: Multileg Routes previously required the Unload Station of one leg to be configured to use the same block face (e.g., east, top) as the Load Station of the next leg.  It now only requires the block to be the same, not the face.
