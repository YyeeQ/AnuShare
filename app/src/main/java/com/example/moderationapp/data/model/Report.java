package com.example.moderationapp.data.model;

import java.util.UUID;

public record Report(UUID message, UUID user, long timestamp, Type type, Priority priority) {
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
        this(message, user, timestamp, Type.OTHER);
    }

    public Report(UUID message, UUID user, long timestamp, Type type) {
        this(message, user, timestamp, type, type == null ? Priority.MEDIUM : type.priority());
    }
}
