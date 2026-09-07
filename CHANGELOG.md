1.0.0
-----
Initial revision.


1.1.0
-----
- Changed: Far And Away screens are now non-pausing by default in single-player mode (they were already non-pausing in multiplayer).  This behavior can be toggled in the mod's config.  The Route Management screen has a 3-second refresh, so in non-pausing mode you can track the progress of Vehicles in real time as they progress from one Waypoint to the next.
- Added: The "Death Route" feature.  When a player dies, a "{Player Name}'s Death Route" is automatically added (or replaced) with a single Waypoint at the location of the player's death.
- Fixed: The distances reported on HUD now take account of vertical distance in addition to horizontal distance.
- Added: Total Waypoints and total Route distance remaining were added to the HUD.
- Added: The directional arrow on the HUD changes to a bullseye target when within horizonal arrival distance of the next Waypoint.
- Added: A vertical direction indicator was added to the HUD.  A single chevron pointing up or down is shown for angles of less than 20 degrees elevation to the next Waypoint, double chevrons for higher angles.
- Added: Added an Invert Route command that inverts the order of a Route's Waypoints, such that the last Waypoint becomes Waypoint 1, the next-to-last Waypoint becomes Waypoint 2, etc.
- Added: Added a Select Route command to select the Route assigned to the current Vehicle.  Saves you the extra step of finding and selecting it in the Route Management screen.
- Added: The Route Manager now automatically scrolls to a newly created Route.
- Changed: When the player has no selected Route and exits a Vehicle, the Vehicle's Route is no longer automatically selected.
- Changed: When mounting a Vehicle, the Vehicle's assigned Route is no longer automatically selected.  The old behavior can be enabled in the mod's config if desired.  Enable that if you prefer being able to view and edit a Vehicle's Waypoints when you mount it without having to issue any additional commands.
- Changed: The HUD no longer displays a Vehicle's generic name if it has a custom name (i.e., a name appied with a nametag).
