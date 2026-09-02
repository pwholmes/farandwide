STATES                              |  BEHAVIORS
                                    |  route name  nav data                             moved by
         route     route     route  |  displayed   displayed  may place  may move       auto-nav
mounted  selected  assigned  active |  on HUD      on HUD     waypoints  independently  system
-------  --------  --------  ------ |  ----------  ---------  ---------  -------------  ---------
n        n         -         -      |  n           n          n          y              n
n        y         n         -      |  y           n          y          y              n
n        y         y         n      |  y           y          y          y              n
n        y         y         y      |  y           y          n          n              y
y        n         -         -      |  n           n          n          y              n
y        y         n         -      |  y           n          y          y              n
y        y         y         n      |  y           y          y          y              n
y        y         y         y      |  y           y          n          n              y

- These behaviors apply speficially to the player.
- The behaviors for independent vehicles, with no player mounted, are much simpler: the rules concerning the HUD and placing waypoints do not apply.  It boils down to: do they have an active, assigned route?  If yes, they follow the assigned route; if not, they behave as normal entities
- "route assigned" means a RouteAssignment exists for the player (if not mounted) or the vehicle (if the player is mounted on it)
- When a player mounts a vehicle:
   - If the vehicle does not have a RouteAssignment, the player's RouteAssignment transfers to the vehicle
   - If the vehicle does have a RouteAssignment, the player's RouteAssignment should be deleted.
- When the player dismounts a vehicle:
   - If the vehicle did not have a RouteAssignment, nothing special happens
   - If the vehicle had a RouteAssignment, the player should get that route as a selected but not assigned route
- The "nav data" is the directional arrow and next waypoint name and distance
- "may move independently" means the vehicle may be controlled manually by the player or, as in the case with horses, move on its own.  Basically, it means, the entity acts as if the mod didn't exist
- The - in the table is a "don't care" state.  A route cannot be assigned or active for the player if the player hasn't selected it, etc.
