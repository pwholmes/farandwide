package com.lastcallsoftware.farandwide.route;

/**
 * Result of an authoritative route request.
 *
 * <p>The server returns one of these for every mutation. The client ignores
 * {@link #SUCCESS} and displays the translation associated with failures. Keep
 * enum ordering stable unless the network protocol version is also changed,
 * because the wire payload encodes the ordinal.
 */
public enum RouteOperationResult {
    SUCCESS,
    ROUTE_NOT_FOUND,
    EMPTY_NAME,
    NO_WAYPOINTS,
    NO_WAYPOINT_IN_DIMENSION,
    NO_ASSIGNMENT,
    ROUTE_ACTIVE;

    public String translationKey() {
        return "message.farandwide.operation." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
