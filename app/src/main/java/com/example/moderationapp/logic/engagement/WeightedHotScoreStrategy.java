package com.example.moderationapp.logic.engagement;

import com.example.moderationapp.data.model.Post;
import com.example.moderationapp.data.model.PostEngagement;

/**
 * Computes a trending score as a weighted sum of visible engagement signals:
 * likes carry the strongest positive weight, replies indicate ongoing
 * discussion, and dislikes act as a moderate dampener. Time-based decay is
 * intentionally omitted so that the score remains directly explainable from
 * the engagement counts shown on each post card.
 */
public class WeightedHotScoreStrategy implements HotScoreStrategy {
    private static final double LIKE_WEIGHT = 3.0;
    private static final double REPLY_WEIGHT = 2.0;
    private static final double DISLIKE_WEIGHT = 1.5;

    @Override
    public double score(Post post, PostEngagement engagement, int replyCount) {
        return engagement.likes() * LIKE_WEIGHT
                + replyCount * REPLY_WEIGHT
                - engagement.dislikes() * DISLIKE_WEIGHT;
    }
}