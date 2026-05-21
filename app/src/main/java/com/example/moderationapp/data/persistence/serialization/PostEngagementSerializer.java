package com.example.moderationapp.data.persistence.serialization;

import com.example.moderationapp.data.model.PostEngagement;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class PostEngagementSerializer implements Serializer<PostEngagement, String[]> {
    @Override
    public String[] serialize(PostEngagement object) {
        return new String[] {
                object.postId().toString(),
                serializeTags(object.tags()),
                serializeCustomTags(object.customTags()),
                object.visibility().name(),
                serializeUsers(object.likedUsers()),
                serializeUsers(object.dislikedUsers()),
                serializeUsers(object.watchedUsers())
        };
    }

    @Override
    public PostEngagement deserialize(String[] data) {
        return new PostEngagement(
                UUID.fromString(data[0]),
                deserializeTags(data[1]),
                data.length > 2 ? deserializeCustomTags(data[2]) : new LinkedHashSet<>(),
                data.length > 3 ? PostEngagement.Visibility.valueOf(data[3]) : PostEngagement.Visibility.PUBLIC,
                data.length > 4 ? deserializeUsers(data[4]) : new HashSet<>(),
                data.length > 5 ? deserializeUsers(data[5]) : new HashSet<>(),
                data.length > 6 ? deserializeUsers(data[6]) : new HashSet<>()
        );
    }

    private String serializeTags(Set<PostEngagement.Tag> tags) {
        return tags.stream()
                .map(PostEngagement.Tag::name)
                .collect(Collectors.joining("|"));
    }

    private Set<PostEngagement.Tag> deserializeTags(String value) {
        Set<PostEngagement.Tag> tags = new LinkedHashSet<>();
        if (value != null && !value.isEmpty()) {
            String[] stored = value.split("\\|");
            for (String item : stored) {
                if (!item.isEmpty()) tags.add(PostEngagement.Tag.fromStoredName(item));
            }
        }
        if (tags.isEmpty()) tags.add(PostEngagement.Tag.ACADEMIC);
        return tags;
    }

    private String serializeCustomTags(Set<String> tags) {
        return String.join("|", tags);
    }

    private Set<String> deserializeCustomTags(String value) {
        Set<String> tags = new LinkedHashSet<>();
        if (value == null || value.isEmpty()) return tags;
        String[] stored = value.split("\\|");
        for (String item : stored) {
            if (!item.trim().isEmpty()) tags.add(item.trim());
        }
        return tags;
    }

    private String serializeUsers(Set<UUID> users) {
        return users.stream()
                .map(UUID::toString)
                .collect(Collectors.joining("|"));
    }

    private Set<UUID> deserializeUsers(String value) {
        Set<UUID> users = new HashSet<>();
        if (value == null || value.isEmpty()) return users;
        String[] ids = value.split("\\|");
        for (String id : ids) {
            if (!id.isEmpty()) users.add(UUID.fromString(id));
        }
        return users;
    }
}
