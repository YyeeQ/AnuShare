package com.example.moderationapp.data.dao;

import com.example.moderationapp.data.model.MessageReports;
import com.example.moderationapp.data.model.Report;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ReportDAO {
    private static ReportDAO instance;

    public static ReportDAO getInstance() {
        if (instance == null) instance = new ReportDAO();
        return instance;
    }

    private final Map<UUID, MessageReports> buckets = new HashMap<>();

    private ReportDAO() {}

    public void clear() {
        buckets.clear();
    }

    public boolean addReport(UUID messageId, UUID userId, long timestamp) {
        return addReport(messageId, userId, timestamp, Report.Type.OTHER);
    }

    public boolean addReport(UUID messageId, UUID userId, long timestamp, Report.Type type) {
        java.util.ArrayList<Report.Type> types = new java.util.ArrayList<>();
        types.add(type == null ? Report.Type.OTHER : type);
        return addReport(messageId, userId, timestamp, types, "");
    }

    public boolean addReport(UUID messageId, UUID userId, long timestamp, List<Report.Type> types, String reason) {
        MessageReports bucket = buckets.computeIfAbsent(messageId, MessageReports::new);
        if (bucket.hasReportFrom(userId)) return false;
        bucket.add(new Report(messageId, userId, timestamp, types, reason));
        return true;
    }

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

    public Iterator<MessageReports> allBuckets() {
        return buckets.values().iterator();
    }

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
