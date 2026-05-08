package dao.model;

import java.util.UUID;

/**
 * A single user's report on a single message at a particular time.
 * Identity is the (message, user) pair: a user can only have one active
 * report on a given message.
 */
public record Report(UUID message, UUID user, long timestamp) {}