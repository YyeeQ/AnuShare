package com.example.moderationapp.data.persistence.serialization;

import com.example.moderationapp.data.model.PostEngagement;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class LegacyPostEngagementSerializer implements Serializer<PostEngagement, String[]> {
    @Override
    public String[] serialize(PostEngagement object) {
        return new String[] {
                object.postId().toString(),
                object.tag().name(),
                serializeUsers(object.likedUsers()),
                serializeUsers(object.dislikedUsers()),
                serializeUsers(object.watchedUsers())
        };
    }

    @Override
    public PostEngagement deserialize(String[] data) {
        return new PostEngagement(
                UUID.fromString(data[0]),
                PostEngagement.Tag.fromStoredName(data[1]),
                deserializeUsers(data[2]),
                deserializeUsers(data[3]),
                deserializeUsers(data[4])
        );
    }

    private String serializeUsers(Set<UUID> users) {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (UUID user : users) {
            if (!first) result.append("|");
            first = false;
            result.append(user);
        }
        return result.toString();
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
