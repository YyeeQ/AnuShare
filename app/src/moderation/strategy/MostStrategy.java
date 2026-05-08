package moderation.strategy;

import dao.model.MessageReports;

import java.util.Comparator;

/**
 * Orders messages by their active report count, highest first.
 */
public class MostStrategy implements ReportStrategy {
    @Override
    public Comparator<MessageReports> comparator() {
        // Reverse natural order so highest count comes first.
        return Comparator.comparingInt(MessageReports::count).reversed();
    }
}