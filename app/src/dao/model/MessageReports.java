package dao.model;

import sorteddata.SortedData;
import sorteddata.SortedDataFactory;

import java.util.Comparator;
import java.util.Iterator;
import java.util.UUID;

/**
 * Groups every Report submitted against a single Message.
 * <p>
 * Reports are kept in a SortedData ordered by (timestamp, user), giving O(log n)
 * access to the oldest report — useful for the "OLDEST" strategy in
 * {@code ModerationTools.getReportedMessages}. The user UUID acts as a tie-breaker
 * so that two distinct users reporting at exactly the same instant are still
 * stored as separate entries.
 * <p>
 * Implements {@link HasUUID} so that a {@code SortedData<MessageReports>} can be
 * indexed by messageId, mirroring the UserDAO / PostDAO pattern of using a
 * SortedData as a Map.
 */
public class MessageReports implements HasUUID {
    /**
     * Orders Reports within a single Message: oldest timestamp first, with
     * user UUID as a deterministic tie-break.
     */
    private static final Comparator<Report> REPORT_ORDER =
            Comparator.comparingLong(Report::timestamp)
                    .thenComparing(Report::user);

    private final UUID messageId;
    private SortedData<Report> reports;

    public MessageReports(UUID messageId) {
        this.messageId = messageId;
        this.reports = SortedDataFactory.makeSortedData(REPORT_ORDER);
    }

    @Override
    public UUID getUUID() {
        return messageId;
    }

    /**
     * Checks whether the given user already has an active report on this message.
     * @param userId the user to check
     * @return true if a report from this user exists, false otherwise
     */
    public boolean hasReportFrom(UUID userId) {
        for (Iterator<Report> it = reports.getAll(); it.hasNext(); ) {
            if (it.next().user().equals(userId)) return true;
        }
        return false;
    }

    /**
     * Adds a new report. The caller is responsible for ensuring the same user
     * does not already have an active report on this message.
     * @param report the report to add
     * @return true if inserted, false if the underlying structure rejected it
     */
    public boolean add(Report report) {
        return reports.insert(report);
    }

    /**
     * Removes the (unique) report from the given user on this message, if one exists.
     * <p>
     * SortedData has no remove operation, so we rebuild the structure without the
     * target entry. This is O(n) in the number of reports on this single message,
     * which is acceptable since per-message report counts are bounded in practice
     * and report removal is a low-frequency action.
     * @param userId the user whose report should be removed
     * @return true if a report was removed, false if no such report existed
     */
    public boolean removeReportFrom(UUID userId) {
        boolean found = false;
        SortedData<Report> rebuilt = SortedDataFactory.makeSortedData(REPORT_ORDER);
        for (Iterator<Report> it = reports.getAll(); it.hasNext(); ) {
            Report r = it.next();
            if (!found && r.user().equals(userId)) {
                found = true;
                continue;
            }
            rebuilt.insert(r);
        }
        if (found) reports = rebuilt;
        return found;
    }

    /**
     * @return the number of active (non-removed) reports on this message
     */
    public int count() {
        int n = 0;
        for (Iterator<Report> it = reports.getAll(); it.hasNext(); ) {
            it.next();
            n++;
        }
        return n;
    }

    /**
     * @return true if this message currently has no active reports
     */
    public boolean isEmpty() {
        return !reports.getAll().hasNext();
    }

    /**
     * @return the timestamp of the oldest active report, or {@code Long.MAX_VALUE}
     *         if there are no active reports
     */
    public long oldestTimestamp() {
        Iterator<Report> it = reports.getAll();
        return it.hasNext() ? it.next().timestamp() : Long.MAX_VALUE;
    }

    /**
     * @return an iterator over all reports on this message, ordered oldest first
     */
    public Iterator<Report> all() {
        return reports.getAll();
    }
}