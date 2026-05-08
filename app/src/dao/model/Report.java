package dao.model;

import java.util.UUID;

/**
 * Represents a single user's report on a single message at a particular point in time.
 * <p>
 * A Report is uniquely identified by the (message, user) pair: a given user can only
 * have one active report on a given message. The timestamp records when the report
 * was submitted, and is used for ordering in moderation views (Task 4).
 *
 * @param message   the UUID of the reported Message
 * @param user      the UUID of the User who submitted the report
 * @param timestamp the UNIX time, in milliseconds, at which the report was submitted
 */
public record Report(UUID message, UUID user, long timestamp) {}