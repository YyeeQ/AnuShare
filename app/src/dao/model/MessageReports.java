package dao.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;

/**
 * All active reports on a single message.
 * <p>
 * Backed by an ArrayList ordered by timestamp. Reports almost always arrive
 * in non-decreasing order (system clock), so we append and only bubble back
 * if needed. ArrayList beats a balanced tree here because per-message report
 * counts are small and we need O(1) size and O(k) targeted removal.
 */
public class MessageReports implements HasUUID {
    private final UUID messageId;
    private final ArrayList<Report> reports = new ArrayList<>();

    public MessageReports(UUID messageId) {
        this.messageId = messageId;
    }

    @Override
    public UUID getUUID() {
        return messageId;
    }

    public boolean hasReportFrom(UUID userId) {
        for (Report r : reports) {
            if (r.user().equals(userId)) return true;
        }
        return false;
    }

    /** Caller must ensure the user does not already have an active report on this message. */
    public void add(Report report) {
        reports.add(report);
        // Restore order if the new report's timestamp is older than its left neighbour.
        for (int i = reports.size() - 1; i > 0; i--) {
            if (reports.get(i).timestamp() < reports.get(i - 1).timestamp()) {
                Report tmp = reports.get(i);
                reports.set(i, reports.get(i - 1));
                reports.set(i - 1, tmp);
            } else {
                break;
            }
        }
    }

    public boolean removeReportFrom(UUID userId) {
        for (int i = 0; i < reports.size(); i++) {
            if (reports.get(i).user().equals(userId)) {
                reports.remove(i);
                return true;
            }
        }
        return false;
    }

    public int count() {
        return reports.size();
    }

    public boolean isEmpty() {
        return reports.isEmpty();
    }

    /** Long.MAX_VALUE if there are no active reports. */
    public long oldestTimestamp() {
        return reports.isEmpty() ? Long.MAX_VALUE : reports.get(0).timestamp();
    }

    /** Iterates oldest first. */
    public Iterator<Report> all() {
        return reports.iterator();
    }
}