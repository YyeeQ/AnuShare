package com.example.moderationapp.data.persistence.serialization;

import com.example.moderationapp.data.model.Message;
import java.util.UUID;

public class MessageSerializer implements Serializer<Message, String[]> {
    @Override
    public String[] serialize(Message object) {
        return new String[] {
                object.id().toString(),
                object.poster().toString(),
                object.thread().toString(),
                String.valueOf(object.timestamp()),
                object.message(),
                object.isHidden() ? "1" : "0"
        };
    }

    @Override
    public Message deserialize(String[] data) {
        return new Message(
                UUID.fromString(data[0]),
                UUID.fromString(data[1]),
                UUID.fromString(data[2]),
                Long.parseLong(data[3]),
                data[4],
                "1".equals(data[5])
        );
    }
}
