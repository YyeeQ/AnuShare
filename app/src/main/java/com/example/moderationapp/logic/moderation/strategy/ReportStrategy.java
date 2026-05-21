package com.example.moderationapp.logic.moderation.strategy;

import com.example.moderationapp.data.model.MessageReports;
import java.util.Comparator;

public interface ReportStrategy {
    Comparator<MessageReports> comparator();
}
