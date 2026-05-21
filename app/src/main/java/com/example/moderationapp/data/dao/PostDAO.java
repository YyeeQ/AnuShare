package com.example.moderationapp.data.dao;

import com.example.moderationapp.data.model.HasUUID;
import com.example.moderationapp.data.model.Message;
import com.example.moderationapp.data.model.Post;
import com.example.moderationapp.logic.sorteddata.sortedarraylist.SortedArrayList;

import java.util.Comparator;
import java.util.Iterator;

public class PostDAO extends DAO<Post> {
    private PostDAO() {
        super(Comparator.comparing(HasUUID::getUUID));
    }
    private static PostDAO instance;

    public static PostDAO getInstance() {
        if (instance == null) instance = new PostDAO();
        return instance;
    }

    public Post getAtIndex(int i) {
        if (data instanceof SortedArrayList) {
            return ((SortedArrayList<Post>) data).getAtIndex(i);
        }
        return null;
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
