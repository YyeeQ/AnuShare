package dao;

import dao.model.HasUUID;
import dao.model.MessageReports;
import dao.model.Report;

import java.util.Comparator;
import java.util.Iterator;
import java.util.UUID;

/**
 * Singleton DAO managing all user-submitted Reports across the application.
 * <p>
 * Reports are organised in a two-level structure:
 * <ul>
 *   <li>An outer {@code SortedData<MessageReports>} indexed by messageId, allowing
 *       O(log n) lookup of "all reports on a given message".</li>
 *   <li>An inner {@code SortedData<Report>} per message, ordered by timestamp,
 *       allowing O(log k) access to the oldest report on that message.</li>
 * </ul>
 * This shape supports every required operation efficiently:
 * <ul>
 *   <li>{@code addReport} / {@code removeReport} / {@code hasReported} —
 *       O(log n + k), where n is the number of reported messages and k is the
 *       number of reports on the targeted message.</li>
 *   <li>Task 4 "OLDEST" / "MOST" strategies — can iterate the outer SortedData
 *       and read each MessageReports' summary in O(1) per message.</li>
 * </ul>
 * Mirrors the Singleton pattern already used by {@link UserDAO} and {@link PostDAO}.
 */
public class ReportDAO extends DAO<MessageReports> {
    private static ReportDAO instance;

    /**
     * @return the singleton instance, creating it on first access
     */
    public static ReportDAO getInstance() {
        if (instance == null) instance = new ReportDAO();
        return instance;
    }

    private ReportDAO() {
        // Index MessageReports by their messageId — same pattern as PostDAO.
        super(Comparator.comparing(HasUUID::getUUID));
    }

    /**
     * Records that a user has reported a message at the given timestamp.
     * <p>
     * Does nothing and returns false if the same user has already reported this
     * message (existence checks for the message and user must be performed by
     * the caller, since this DAO does not own those records).
     *
     * @param messageId the reported message
     * @param userId    the reporting user
     * @param timestamp the time the report was submitted, in UNIX ms
     * @return true if a new report was stored, false if a duplicate existed
     */
    public boolean addReport(UUID messageId, UUID userId, long timestamp) {
        MessageReports bucket = data.get(new MessageReports(messageId));
        if (bucket == null) {
            bucket = new MessageReports(messageId);
            data.insert(bucket);
        } else if (bucket.hasReportFrom(userId)) {
            return false;
        }
        return bucket.add(new Report(messageId, userId, timestamp));
    }

    /**
     * Removes the report a particular user filed against a particular message.
     * @param messageId the message in question
     * @param userId    the user whose report should be retracted
     * @return true if a matching report was found and removed, false otherwise
     */
    public boolean removeReport(UUID messageId, UUID userId) {
        MessageReports bucket = data.get(new MessageReports(messageId));
        if (bucket == null) return false;
        return bucket.removeReportFrom(userId);
    }

    /**
     * Checks whether a particular user has an active report on a particular message.
     * @param messageId the message in question
     * @param userId    the user in question
     * @return true if such a report currently exists, false otherwise
     */
    public boolean hasReported(UUID messageId, UUID userId) {
        MessageReports bucket = data.get(new MessageReports(messageId));
        return bucket != null && bucket.hasReportFrom(userId);
    }

    /**
     * @param messageId the message in question
     * @return the MessageReports bucket for this message, or null if none exists
     */
    public MessageReports getReportsFor(UUID messageId) {
        return data.get(new MessageReports(messageId));
    }

    /**
     * @return an iterator over every MessageReports bucket currently stored,
     *         in messageId order. Useful for Task 4's reporting views.
     */
    public Iterator<MessageReports> allBuckets() {
        return data.getAll();
    }
}