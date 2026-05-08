package moderation.strategy;

import dao.model.MessageReports;

import java.util.Comparator;

/**
 * Orders messages by the timestamp of their oldest active report,
 * earliest first.
 */
public class OldestStrategy implements ReportStrategy {
    @Override
    public Comparator<MessageReports> comparator() {
        return Comparator.comparingLong(MessageReports::oldestTimestamp);
    }
}

