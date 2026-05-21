package com.example.moderationapp.logic.engagement;

import com.example.moderationapp.data.model.Post;
import com.example.moderationapp.data.model.PostEngagement;

public class WeightedHotScoreStrategy implements HotScoreStrategy {
    private static final long ONE_HOUR_MS = 60L * 60L * 1000L;

    @Override
    public double score(Post post, PostEngagement engagement, int replyCount, long newestActivityTimestamp) {
        long ageHours = Math.max(1L, (System.currentTimeMillis() - newestActivityTimestamp) / ONE_HOUR_MS);
        double freshness = 12.0 / Math.sqrt(ageHours);
        return freshness
                + engagement.likes() * 3.0
                + engagement.watches() * 2.0
                + replyCount * 1.5
                - engagement.dislikes() * 2.0;
    }
}
