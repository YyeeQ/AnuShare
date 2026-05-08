package dao.model;

import java.util.UUID;

/**
 * A reply to a Post.
 * <p>
 * Promoted from a record to a class so the {@code hidden} flag can be mutated
 * by moderators without rebuilding every container holding the Message.
 * Accessor names match the original record API so existing callers compile
 * unchanged.
 */
public class Message {
    private final UUID id;
    private final UUID poster;
    private final UUID thread;
    private final long timestamp;
    private final String message;

    private boolean hidden;

    public Message(UUID id, UUID poster, UUID thread, long timestamp, String message) {
        this(id, poster, thread, timestamp, message, false);
    }

    /** Used by the persistence layer to restore a Message together with its hidden flag. */
    public Message(UUID id, UUID poster, UUID thread, long timestamp, String message, boolean hidden) {
        this.id = id;
        this.poster = poster;
        this.thread = thread;
        this.timestamp = timestamp;
        this.message = message;
        this.hidden = hidden;
    }

    public UUID id()        { return id; }
    public UUID poster()    { return poster; }
    public UUID thread()    { return thread; }
    public long timestamp() { return timestamp; }
    public String message() { return message; }

    public boolean isHidden() { return hidden; }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
}