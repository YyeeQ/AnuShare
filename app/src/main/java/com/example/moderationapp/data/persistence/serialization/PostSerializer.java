package com.example.moderationapp.data.persistence.serialization;

import com.example.moderationapp.data.model.Post;
import java.util.UUID;

public class PostSerializer implements Serializer<Post, String[]> {
    @Override
    public String[] serialize(Post object) {
        return new String[] {object.id.toString(), object.poster.toString(), object.topic};
    }

    @Override
    public Post deserialize(String[] data) {
        return new Post(UUID.fromString(data[0]), UUID.fromString(data[1]), data[2]);
    }
}
