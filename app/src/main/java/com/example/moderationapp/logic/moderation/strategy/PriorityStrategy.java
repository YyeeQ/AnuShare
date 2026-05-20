package com.example.moderationapp.logic.moderation.strategy;

import com.example.moderationapp.data.model.MessageReports;

import java.util.Comparator;

public class PriorityStrategy implements ReportStrategy {
    @Override
    public Comparator<MessageReports> comparator() {
        return (left, right) -> {
            int priorityDelta = Integer.compare(
                    right.highestPriority().rank(),
                    left.highestPriority().rank());
            if (priorityDelta != 0) return priorityDelta;

            int countDelta = Integer.compare(right.count(), left.count());
            if (countDelta != 0) return countDelta;

            return Long.compare(left.oldestTimestamp(), right.oldestTimestamp());
        };
    }
}
