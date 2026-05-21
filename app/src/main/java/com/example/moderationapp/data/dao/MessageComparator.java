package com.example.moderationapp.data.dao;

import com.example.moderationapp.data.model.Message;
import java.util.Comparator;

public class MessageComparator implements Comparator<Message> {
    private static MessageComparator instance;

    public static MessageComparator getInstance() {
        if (instance == null) instance = new MessageComparator();
        return instance;
    }
    private MessageComparator() {}

    @Override
    public int compare(Message o1, Message o2) {
        int delta = Long.compare(o1.timestamp(), o2.timestamp());
        if (delta != 0) return delta;

        delta = o1.thread().compareTo(o2.thread());
        if (delta != 0) return delta;

        delta = o1.poster().compareTo(o2.poster());
        if (delta != 0) return delta;

        return o1.id().compareTo(o2.id());
    }
}
