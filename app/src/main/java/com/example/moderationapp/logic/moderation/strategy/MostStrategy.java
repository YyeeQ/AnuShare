package com.example.moderationapp.logic.moderation.strategy;

import com.example.moderationapp.data.model.MessageReports;
import java.util.Comparator;

public class MostStrategy implements ReportStrategy {
    @Override
    public Comparator<MessageReports> comparator() {
        return (o1, o2) -> Integer.compare(o2.count(), o1.count());
    }
}
