package dao;

import dao.model.MessageReports;
import dao.model.Report;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Stores all user-submitted Reports.
 * <p>
 * Two-level structure: a HashMap from messageId to a MessageReports bucket,
 * with each bucket holding that message's reports in an ArrayList. HashMap
 * gives O(1) bucket lookup; we don't need messageIds in any sorted order so
 * a tree would only add overhead. Tasks that need ordering (Task 4's OLDEST
 * and MOST strategies) sort the buckets at query time.
 */
public class ReportDAO {
    private static ReportDAO instance;

    public static ReportDAO getInstance() {
        if (instance == null) instance = new ReportDAO();
        return instance;
    }

    private final Map<UUID, MessageReports> buckets = new HashMap<>();

    private ReportDAO() {}

    /** Used by the persistence layer on reload. */
    public void clear() {
        buckets.clear();
    }

    /**
     * Existence checks for the message and user are the caller's responsibility.
     * @return false if the user already had an active report on this message
     */
    public boolean addReport(UUID messageId, UUID userId, long timestamp) {
        MessageReports bucket = buckets.computeIfAbsent(messageId, MessageReports::new);
        if (bucket.hasReportFrom(userId)) return false;
        bucket.add(new Report(messageId, userId, timestamp));
        return true;
    }

    /**
     * Restore path used during deserialisation. No cross-DAO checks; trusts
     * the data on disk to be internally consistent.
     */
    public void addExisting(Report report) {
        MessageReports bucket = buckets.computeIfAbsent(report.message(), MessageReports::new);
        bucket.add(report);
    }

    public boolean removeReport(UUID messageId, UUID userId) {
        MessageReports bucket = buckets.get(messageId);
        if (bucket == null) return false;
        return bucket.removeReportFrom(userId);
    }

    public boolean hasReported(UUID messageId, UUID userId) {
        MessageReports bucket = buckets.get(messageId);
        return bucket != null && bucket.hasReportFrom(userId);
    }

    public MessageReports getReportsFor(UUID messageId) {
        return buckets.get(messageId);
    }

    /** Used by Task 4. */
    public Iterator<MessageReports> allBuckets() {
        return buckets.values().iterator();
    }

    /** Used by the persistence layer to write reports.txt. */
    public Iterator<Report> allReports() {
        return new Iterator<>() {
            private final Iterator<MessageReports> bucketIt = buckets.values().iterator();
            private Iterator<Report> current = null;
            private Report next = null;
            { advance(); }

            private void advance() {
                while (current == null || !current.hasNext()) {
                    if (!bucketIt.hasNext()) { next = null; return; }
                    current = bucketIt.next().all();
                }
                next = current.next();
            }

            @Override
            public boolean hasNext() { return next != null; }

            @Override
            public Report next() {
                Report n = next;
                advance();
                return n;
            }
        };
    }
}