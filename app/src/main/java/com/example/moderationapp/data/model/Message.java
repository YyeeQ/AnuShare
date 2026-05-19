package com.example.moderationapp.data.model;

import java.util.UUID;

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
