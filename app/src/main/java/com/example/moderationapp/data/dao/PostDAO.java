package com.example.moderationapp.data.dao;

import com.example.moderationapp.data.model.HasUUID;
import com.example.moderationapp.data.model.Message;
import com.example.moderationapp.data.model.Post;
import com.example.moderationapp.logic.sorteddata.sortedarraylist.SortedArrayList;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class PostDAO extends DAO<Post> {
    private PostDAO() {
        super(Comparator.comparing(HasUUID::getUUID));
    }
    private static PostDAO instance;

    public static PostDAO getInstance() {
        if (instance == null) instance = new PostDAO();
        return instance;
    }

    /** Lazy index: UUID -> Message. Built on first lookup, then kept warm. */
    private final Map<UUID, Message> messageIndex = new HashMap<>();
    private boolean messageIndexBuilt = false;

    public Post getAtIndex(int i) {
        if (data instanceof SortedArrayList) {
            return ((SortedArrayList<Post>) data).getAtIndex(i);
        }
        return null;
    }

    /**
     * O(1) average lookup of any Message currently held by any Post in this DAO.
     * Returns null if no such Message exists.
     *
     * On first call (or after invalidation) the entire message graph is scanned
     * once to populate the index; subsequent calls are O(1).
     */
    public Message getMessageByUUID(UUID messageId) {
        if (messageId == null) return null;
        if (!messageIndexBuilt) rebuildMessageIndex();

        Message cached = messageIndex.get(messageId);
        if (cached != null) return cached;

        // Cache miss: a new Message may have been inserted into a Post since
        // we last built the index. Rebuild once and re-check.
        rebuildMessageIndex();
        return messageIndex.get(messageId);
    }

    /** Force the next {@link #getMessageByUUID} call to rescan. */
    public void invalidateMessageIndex() {
        messageIndexBuilt = false;
        messageIndex.clear();
    }

    private void rebuildMessageIndex() {
        messageIndex.clear();
        for (Iterator<Post> postIt = getAll(); postIt.hasNext(); ) {
            Post post = postIt.next();
            for (Iterator<Message> messageIt = post.messages.getAll(); messageIt.hasNext(); ) {
                Message message = messageIt.next();
                messageIndex.put(message.id(), message);
            }
        }
        messageIndexBuilt = true;
    }

    public Iterator<Message> getAllMessages() {
        return new Iterator<>() {
            private final Iterator<Post> postIterator = getAll();
            private Iterator<Message> currentPost = null;
            private Message next = null;
            {
                goNext();
            }

            private void goNext() {
                while (currentPost == null || !currentPost.hasNext()) {
                    if (postIterator.hasNext())
                        currentPost = postIterator.next().messages.getAll();
                    else {
                        next = null;
                        return;
                    }
                }
                next = currentPost.next();
            }

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public Message next() {
                Message thisOne = next;
                goNext();
                return thisOne;
            }
        };
    }
}