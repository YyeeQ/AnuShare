package com.example.moderationapp.data.dao;

import com.example.moderationapp.data.model.HasUUID;
import com.example.moderationapp.data.model.PostEngagement;

import java.util.Comparator;

public class PostEngagementDAO extends DAO<PostEngagement> {
    private static PostEngagementDAO instance;

    public static PostEngagementDAO getInstance() {
        if (instance == null) instance = new PostEngagementDAO();
        return instance;
    }

    private PostEngagementDAO() {
        super(Comparator.comparing(HasUUID::getUUID));
    }
}
