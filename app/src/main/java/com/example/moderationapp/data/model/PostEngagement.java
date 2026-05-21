package com.example.moderationapp.data.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class PostEngagement implements HasUUID {
    public enum Tag {
        ACADEMIC("Academic"),
        CAMPUS("Campus"),
        LOUNGE("Lounge");

        private final String label;

        Tag(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public static Tag fromStoredName(String value) {
            if (value == null || value.isEmpty()) return ACADEMIC;
            try {
                return Tag.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                switch (value) {
                    case "EDUCATION":
                        return ACADEMIC;
                    case "HOUSING":
                    case "EVENTS":
                        return CAMPUS;
                    case "LIFE":
                    case "FOOD":
                    case "WELLBEING":
                        return LOUNGE;
                    default:
                        return ACADEMIC;
                }
            }
        }
    }

    public enum Visibility {
        PUBLIC("Public"),
        ADMIN_ONLY("Admins only"),
        PRIVATE("Private");

        private final String label;

        Visibility(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final UUID postId;
    private final Set<Tag> tags;
    private Visibility visibility;
    private final Set<UUID> likedBy;
    private final Set<UUID> dislikedBy;
    private final Set<UUID> watchedBy;

    public PostEngagement(UUID postId, Tag tag) {
        this(postId, initialTags(tag), Visibility.PUBLIC, new HashSet<>(), new HashSet<>(), new HashSet<>());
    }

    public PostEngagement(UUID postId, Tag tag, Set<UUID> likedBy, Set<UUID> dislikedBy, Set<UUID> watchedBy) {
        this(postId, initialTags(tag), Visibility.PUBLIC, likedBy, dislikedBy, watchedBy);
    }

    public PostEngagement(
            UUID postId,
            Set<Tag> tags,
            Visibility visibility,
            Set<UUID> likedBy,
            Set<UUID> dislikedBy,
            Set<UUID> watchedBy) {
        this.postId = postId;
        this.tags = sanitizeTags(tags);
        this.visibility = visibility == null ? Visibility.PUBLIC : visibility;
        this.likedBy = likedBy == null ? new HashSet<>() : new HashSet<>(likedBy);
        this.dislikedBy = dislikedBy == null ? new HashSet<>() : new HashSet<>(dislikedBy);
        this.watchedBy = watchedBy == null ? new HashSet<>() : new HashSet<>(watchedBy);
    }

    public PostEngagement(UUID postId) {
        this(postId, Tag.ACADEMIC);
    }

    public UUID postId() {
        return postId;
    }

    public Tag tag() {
        return tags.iterator().next();
    }

    public void setTag(Tag tag) {
        if (tag == null) return;
        tags.clear();
        tags.add(tag);
    }

    public Set<Tag> tags() {
        return Collections.unmodifiableSet(tags);
    }

    public void setTags(Set<Tag> tags) {
        this.tags.clear();
        this.tags.addAll(sanitizeTags(tags));
    }

    public Visibility visibility() {
        return visibility;
    }

    public void setVisibility(Visibility visibility) {
        if (visibility != null) this.visibility = visibility;
    }

    public boolean toggleLike(UUID userId) {
        if (userId == null) return false;
        dislikedBy.remove(userId);
        return likedBy.contains(userId) ? likedBy.remove(userId) : likedBy.add(userId);
    }

    public boolean toggleDislike(UUID userId) {
        if (userId == null) return false;
        likedBy.remove(userId);
        return dislikedBy.contains(userId) ? dislikedBy.remove(userId) : dislikedBy.add(userId);
    }

    public boolean toggleWatch(UUID userId) {
        if (userId == null) return false;
        return watchedBy.contains(userId) ? watchedBy.remove(userId) : watchedBy.add(userId);
    }

    public boolean likedBy(UUID userId) {
        return likedBy.contains(userId);
    }

    public boolean dislikedBy(UUID userId) {
        return dislikedBy.contains(userId);
    }

    public boolean watchedBy(UUID userId) {
        return watchedBy.contains(userId);
    }

    public int likes() {
        return likedBy.size();
    }

    public int dislikes() {
        return dislikedBy.size();
    }

    public int watches() {
        return watchedBy.size();
    }

    public Set<UUID> likedUsers() {
        return Collections.unmodifiableSet(likedBy);
    }

    public Set<UUID> dislikedUsers() {
        return Collections.unmodifiableSet(dislikedBy);
    }

    public Set<UUID> watchedUsers() {
        return Collections.unmodifiableSet(watchedBy);
    }

    @Override
    public UUID getUUID() {
        return postId;
    }

    private Set<Tag> sanitizeTags(Set<Tag> source) {
        Set<Tag> result = new LinkedHashSet<>();
        if (source != null) {
            for (Tag tag : source) {
                if (tag != null) result.add(tag);
            }
        }
        if (result.isEmpty()) result.add(Tag.ACADEMIC);
        return result;
    }

    private static Set<Tag> initialTags(Tag tag) {
        Set<Tag> tags = new LinkedHashSet<>();
        tags.add(tag == null ? Tag.ACADEMIC : tag);
        return tags;
    }
}