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

- These behaviors apply specifically to the player. "Selected" means the
  player's UI selection. "Assigned" and "active" refer to the assignment owned
  by the controlled assignee: the player while on foot or the ridden Vehicle
  while mounted.
- The behaviors for independent vehicles, with no player mounted, are much simpler: the rules concerning the HUD and placing waypoints do not apply.  It boils down to: do they have an active, assigned route?  If yes, they follow the assigned route; if not, they behave as normal entities
- "route assigned" means the controlled assignee has a RouteAssignment for the
  selected Route. Selection does not create, replace, pause, or remove an
  assignment, so the selected and assigned Routes may differ.
- When a player mounts a vehicle:
   - If the vehicle does not have a RouteAssignment, the player's RouteAssignment transfers to the vehicle
   - If the vehicle does have a RouteAssignment, the player's RouteAssignment should be deleted.
- When the player dismounts a vehicle:
   - If the vehicle did not have a RouteAssignment, nothing special happens
   - If the vehicle had a RouteAssignment, the player should get that route as a selected but not assigned route
- The HUD labels the selected Route separately from the controlled assignee's
  assigned Route. The assignment line appears only when an assignment exists.
  The assignee is shown as "Player" on foot, or by its generic Vehicle type
  followed by its tagged name, if any. Each Route line ends with the traversal
  type icon that applies to it. The "nav data" is the directional arrow and next
  waypoint name and distance for the assigned Route.
- Each assignment has a traversal direction. The Route Management entry shows
  + for increasing waypoint numbers and - for decreasing waypoint numbers;
  clicking it reverses the assignment and targets the adjacent waypoint in the
  new direction.
- "may move independently" means the vehicle may be controlled manually by the player or, as in the case with horses, move on its own.  Basically, it means, the entity acts as if the mod didn't exist
- The - in the table is a "don't care" state. An assignment may remain active
  when its Route is not selected.
