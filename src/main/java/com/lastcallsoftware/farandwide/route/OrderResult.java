package com.lastcallsoftware.farandwide.route;

import org.eclipse.jdt.annotation.NonNullByDefault;

/** Results specific to placing and removing tracking records. */
@NonNullByDefault
public enum OrderResult {
    PLACED, CANCELLED, INVALID_ORDER, INVALID_ENDPOINTS, INVALID_SOURCES,
    INSUFFICIENT_STOCK, LOAD_STATION_FULL, FILTER_REJECTED, CHUNK_LOADING_DISABLED,
    TRACKING_FULL, NOT_FOUND, UNSUPPORTED_TRAVERSAL;

    public String translationKey() {
        return "message.farandwide.order." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
