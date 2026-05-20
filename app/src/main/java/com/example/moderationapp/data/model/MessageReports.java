package com.example.moderationapp.data.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;

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

    public void add(Report report) {
        reports.add(report);
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

    public long oldestTimestamp() {
        return reports.isEmpty() ? Long.MAX_VALUE : reports.get(0).timestamp();
    }

    public Report.Priority highestPriority() {
        Report.Priority highest = Report.Priority.LOW;
        for (Report report : reports) {
            if (report.priority().rank() > highest.rank()) {
                highest = report.priority();
            }
        }
        return highest;
    }

    public Iterator<Report> all() {
        return reports.iterator();
    }
}
