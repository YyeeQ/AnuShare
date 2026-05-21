package com.example.moderationapp.logic.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Formats a Unix timestamp into a human-friendly relative time string.
 *
 * Examples:
 *   - 30 seconds ago    -> "just now"
 *   - 5 minutes ago     -> "5 minutes ago"
 *   - 1 hour ago        -> "1 hour ago"
 *   - 3 days ago        -> "3 days ago"
 *   - 12 days ago       -> "May 7"
 *
 * Stateless utility - all methods are static.
 */
public final class TimeFormatter {

    private static final long ONE_MINUTE_MS = 60L * 1000L;
    private static final long ONE_HOUR_MS   = 60L * ONE_MINUTE_MS;
    private static final long ONE_DAY_MS    = 24L * ONE_HOUR_MS;
    private static final long ONE_WEEK_MS   = 7L * ONE_DAY_MS;

    private TimeFormatter() {
        // Utility class - no instances.
    }

    /**
     * Format a timestamp as a relative time string, using the current system time
     * as the reference point.
     */
    public static String relative(long timestamp) {
        return relative(timestamp, System.currentTimeMillis());
    }

    /**
     * Format {@code timestamp} relative to {@code now}.
     * Visible for testing - lets callers supply a fixed "now" for deterministic tests.
     */
    public static String relative(long timestamp, long now) {
        long diff = now - timestamp;

        // Future timestamps (clock skew, bad input, etc.) display as "just now".
        if (diff < 0) return "just now";

        if (diff < ONE_MINUTE_MS) {
            return "just now";
        }
        if (diff < ONE_HOUR_MS) {
            long minutes = diff / ONE_MINUTE_MS;
            return minutes + " minute" + plural(minutes) + " ago";
        }
        if (diff < ONE_DAY_MS) {
            long hours = diff / ONE_HOUR_MS;
            return hours + " hour" + plural(hours) + " ago";
        }
        if (diff < ONE_WEEK_MS) {
            long days = diff / ONE_DAY_MS;
            return days + " day" + plural(days) + " ago";
        }

        // Older than a week - show absolute date (e.g. "May 7").
        return new SimpleDateFormat("MMM d", Locale.getDefault()).format(new Date(timestamp));
    }

    /**
     * Compact form for tight UI (avatars next to author lines, etc.).
     *
     * Examples: "now", "5m", "3h", "2d", "May 7"
     */
    public static String relativeShort(long timestamp) {
        return relativeShort(timestamp, System.currentTimeMillis());
    }

    public static String relativeShort(long timestamp, long now) {
        long diff = now - timestamp;

        if (diff < 0) return "now";

        if (diff < ONE_MINUTE_MS) return "now";
        if (diff < ONE_HOUR_MS)   return (diff / ONE_MINUTE_MS) + "m";
        if (diff < ONE_DAY_MS)    return (diff / ONE_HOUR_MS)   + "h";
        if (diff < ONE_WEEK_MS)   return (diff / ONE_DAY_MS)    + "d";

        return new SimpleDateFormat("MMM d", Locale.getDefault()).format(new Date(timestamp));
    }

    public static String exact(long timestamp) {
        return new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(new Date(timestamp));
    }

    private static String plural(long n) {
        return n == 1 ? "" : "s";
    }
}
