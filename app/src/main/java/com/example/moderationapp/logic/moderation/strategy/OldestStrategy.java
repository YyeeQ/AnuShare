package com.example.moderationapp.logic.moderation.strategy;

import com.example.moderationapp.data.model.MessageReports;
import java.util.Comparator;

public class OldestStrategy implements ReportStrategy {
    @Override
    public Comparator<MessageReports> comparator() {
        return (o1, o2) -> Long.compare(o1.oldestTimestamp(), o2.oldestTimestamp());
    }
}
