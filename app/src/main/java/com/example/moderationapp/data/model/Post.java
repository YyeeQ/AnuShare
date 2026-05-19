package com.example.moderationapp.data.model;

import com.example.moderationapp.logic.sorteddata.SortedData;
import com.example.moderationapp.logic.sorteddata.SortedDataFactory;
import com.example.moderationapp.data.dao.MessageComparator;

import java.util.Iterator;
import java.util.UUID;

public class Post implements HasUUID {
    public final UUID id;
    public final UUID poster;
    public final String topic;
    public final SortedData<Message> messages;

    public Post(UUID id, UUID poster, String topic) {
        this.id = id;
        this.poster = poster;
        this.topic = topic;
        this.messages = SortedDataFactory.makeSortedData(MessageComparator.getInstance());
    }

    public Post(UUID id) {
        this(id, null, null);
    }

    public SortedData<Message> getVisibleMessages(boolean isAdmin) {
        if (isAdmin) {
            return messages;
        }

        SortedData<Message> visible =
                SortedDataFactory.makeSortedData(MessageComparator.getInstance());

        Iterator<Message> it = messages.getAll();

        while (it.hasNext()) {
            Message message = it.next();

            if (!message.isHidden()) {
                visible.insert(message);
            }
        }

        return visible;
    }

    @Override
    public UUID getUUID() {
        return id;
    }
}
