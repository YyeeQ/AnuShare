package com.example.moderationapp.data.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Report {
    private final UUID message;
    private final UUID user;
    private final long timestamp;
    private final List<Type> types;
    private final String reason;
    private final Priority priority;

    public enum Priority {
        LOW(0),
        MEDIUM(1),
        HIGH(2),
        URGENT(3);

        private final int rank;

        Priority(int rank) {
            this.rank = rank;
        }

        public int rank() {
            return rank;
        }

        public static Priority fromStorage(String value) {
            for (Priority priority : values()) {
                if (priority.name().equalsIgnoreCase(value)) return priority;
            }
            return MEDIUM;
        }
    }

    public enum Type {
        SPAM("Spam", Priority.LOW),
        HARASSMENT("Harassment", Priority.MEDIUM),
        HATE_SPEECH("Hate Speech", Priority.HIGH),
        VIOLENCE("Violence", Priority.URGENT),
        OTHER("Other", Priority.MEDIUM);

        private final String label;
        private final Priority priority;

        Type(String label, Priority priority) {
            this.label = label;
            this.priority = priority;
        }

        public String label() {
            return label;
        }

        public Priority priority() {
            return priority;
        }

        public static Type fromLabel(String label) {
            for (Type type : values()) {
                if (type.label.equalsIgnoreCase(label)) return type;
            }
            return OTHER;
        }

        public static Type fromStorage(String value) {
            for (Type type : values()) {
                if (type.name().equalsIgnoreCase(value)) return type;
            }
            return OTHER;
        }
    }

    public Report(UUID message, UUID user, long timestamp) {
        this(message, user, timestamp, Type.OTHER, "");
    }

    public Report(UUID message, UUID user, long timestamp, Type type) {
        this(message, user, timestamp, type, "");
    }

    public Report(UUID message, UUID user, long timestamp, Type type, String reason) {
        List<Type> selected = new ArrayList<>();
        selected.add(type == null ? Type.OTHER : type);
        this.message = message;
        this.user = user;
        this.timestamp = timestamp;
        this.types = sanitizeTypes(selected);
        this.reason = reason == null ? "" : reason;
        this.priority = highestPriority(this.types);
    }

    public Report(UUID message, UUID user, long timestamp, List<Type> types, String reason) {
        this.message = message;
        this.user = user;
        this.timestamp = timestamp;
        this.types = sanitizeTypes(types);
        this.reason = reason == null ? "" : reason;
        this.priority = highestPriority(this.types);
    }

    public UUID message() {
        return message;
    }

    public UUID user() {
        return user;
    }

    public long timestamp() {
        return timestamp;
    }

    public Type type() {
        return types.isEmpty() ? Type.OTHER : types.get(0);
    }

    public List<Type> types() {
        return Collections.unmodifiableList(types);
    }

    public String reason() {
        return reason;
    }

    public Priority priority() {
        return priority;
    }

    private static List<Type> sanitizeTypes(List<Type> source) {
        List<Type> result = new ArrayList<>();
        if (source != null) {
            for (Type type : source) {
                if (type != null && !result.contains(type)) result.add(type);
            }
        }
        if (result.isEmpty()) result.add(Type.OTHER);
        return result;
    }

    private static Priority highestPriority(List<Type> types) {
        Priority highest = Priority.LOW;
        for (Type type : types) {
            if (type.priority().rank() > highest.rank()) {
                highest = type.priority();
            }
        }
        return highest;
    }
}
