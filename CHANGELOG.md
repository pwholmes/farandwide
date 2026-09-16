1.0.0
-----
Initial revision.


1.1.0
-----
- Changed: Far And Away screens are now non-pausing by default in single-player mode (they were already non-pausing in multiplayer).  This behavior can be toggled in the mod's config.  The Route Management screen has a 3-second refresh, so in non-pausing mode you can track the progress of Vehicles in real time as they progress from one Waypoint to the next.
- Added: The "Death Route" feature.  When a player dies, a "(Player Name)'s Death Route" is automatically added (or replaced) with a single Waypoint at the location of the player's death.
- Fixed: The distances reported on HUD only calculated horizontal distance.  It now takes account of vertical distance as well.
- Added: Total Waypoints and total Route distance remaining were added to the HUD.
- Added: The directional arrow on the HUD changes to a bullseye target when within horizonal arrival distance of the next Waypoint.
- Added: A vertical direction indicator was added to the HUD.  A single chevron pointing up or down is shown for angles of less than 20 degrees elevation to the next Waypoint, double chevrons for higher angles.
- Added: Added an Invert Route command that inverts the order of a Route's Waypoints, such that the last Waypoint becomes Waypoint 1, the next-to-last Waypoint becomes Waypoint 2, etc.
- Added: Added a Select Route command to select the Route assigned to the current Vehicle.  Saves you the extra step of finding and selecting it in the Route Management screen.
- Added: The Route Manager now automatically scrolls to a newly created Route.
- Changed: When the player has no selected Route and exits a Vehicle, the Vehicle's Route is no longer automatically selected.
- Changed: When mounting a Vehicle, the Vehicle's assigned Route is no longer automatically selected.  The old behavior can be enabled in the mod's config if desired.  Enable that if you prefer being able to view and edit a Vehicle's Waypoints when you mount it without having to issue any additional commands.
- Changed: The HUD no longer displays a Vehicle's generic name if it has a custom name (i.e., a name appied with a nametag).


1.2.0
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
