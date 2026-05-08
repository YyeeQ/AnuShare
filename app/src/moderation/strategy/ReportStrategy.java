package moderation.strategy;

import dao.model.MessageReports;

import java.util.Comparator;

/**
 * Defines an ordering over MessageReports buckets for the moderation view.
 * <p>
 * Implementations are produced by {@link ReportStrategyFactory} based on the
 * strategy name supplied to {@link moderation.ModerationTools#getReportedMessages}.
 */
public interface ReportStrategy {
    /** Comparator placing higher-priority buckets earlier in the result. */
    Comparator<MessageReports> comparator();
}