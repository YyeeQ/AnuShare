package com.example.moderationapp.logic.engagement;

import com.example.moderationapp.data.model.Post;
import com.example.moderationapp.data.model.PostEngagement;

public interface HotScoreStrategy {
    double score(Post post, PostEngagement engagement, int replyCount, long newestActivityTimestamp);
}
