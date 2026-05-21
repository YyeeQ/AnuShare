package com.example.moderationapp.logic.engagement;

import com.example.moderationapp.data.dao.PostDAO;
import com.example.moderationapp.data.dao.PostEngagementDAO;
import com.example.moderationapp.data.model.Message;
import com.example.moderationapp.data.model.Post;
import com.example.moderationapp.data.model.PostEngagement;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class PostEngagementService {
    private static PostEngagementService instance;

    public static PostEngagementService getInstance() {
        if (instance == null) instance = new PostEngagementService(new WeightedHotScoreStrategy());
        return instance;
    }

    private final PostEngagementDAO engagements = PostEngagementDAO.getInstance();
    private final HotScoreStrategy hotScoreStrategy;

    private PostEngagementService(HotScoreStrategy hotScoreStrategy) {
        this.hotScoreStrategy = hotScoreStrategy;
    }

    public PostEngagement ensureMetadata(UUID postId) {
        return ensureMetadata(postId, defaultTagFor(postId));
    }

    public PostEngagement ensureMetadata(UUID postId, PostEngagement.Tag tag) {
        PostEngagement existing = engagements.get(new PostEngagement(postId));
        if (existing != null) return existing;
        PostEngagement created = new PostEngagement(postId, tag);
        engagements.add(created);
        return created;
    }

    public boolean ensureMetadataForExistingPosts() {
        boolean changed = false;
        for (Iterator<Post> it = PostDAO.getInstance().getAll(); it.hasNext(); ) {
            Post post = it.next();
            if (engagements.get(new PostEngagement(post.id)) == null) {
                engagements.add(new PostEngagement(post.id, defaultTagFor(post.id)));
                changed = true;
            }
        }
        return changed;
    }

    public void setTag(UUID postId, PostEngagement.Tag tag) {
        ensureMetadata(postId, tag).setTag(tag);
    }

    public void updatePostSettings(
            UUID postId,
            Set<PostEngagement.Tag> tags,
            Set<String> customTags,
            PostEngagement.Visibility visibility) {
        PostEngagement metadata = ensureMetadata(postId);
        metadata.setTags(tags, customTags);
        metadata.setVisibility(visibility);
    }

    public void toggleLike(UUID postId, UUID userId) {
        ensureMetadata(postId).toggleLike(userId);
    }

    public void toggleDislike(UUID postId, UUID userId) {
        ensureMetadata(postId).toggleDislike(userId);
    }

    public void toggleWatch(UUID postId, UUID userId) {
        ensureMetadata(postId).toggleWatch(userId);
    }

    public List<Post> trendingPosts(PostEngagement.Tag tag, boolean isAdmin) {
        List<Post> posts = new ArrayList<>();
        for (Iterator<Post> it = PostDAO.getInstance().getAll(); it.hasNext(); ) {
            Post post = it.next();
            PostEngagement engagement = ensureMetadata(post.id);
            if (tag == null || engagement.tags().contains(tag)) posts.add(post);
        }
        posts.sort((left, right) -> Double.compare(score(right, isAdmin), score(left, isAdmin)));
        return posts;
    }

    public double score(Post post, boolean isAdmin) {
        return hotScoreStrategy.score(
                post,
                ensureMetadata(post.id),
                replyCount(post, isAdmin),
                newestActivityTimestamp(post, isAdmin));
    }

    private int replyCount(Post post, boolean isAdmin) {
        int count = 0;
        for (Iterator<Message> it = post.getVisibleMessages(isAdmin).getAll(); it.hasNext(); ) {
            it.next();
            count++;
        }
        return Math.max(0, count - 1);
    }

    private long newestActivityTimestamp(Post post, boolean isAdmin) {
        Iterator<Message> it = post.getVisibleMessages(isAdmin).getAll();
        return it.hasNext() ? it.next().timestamp() : System.currentTimeMillis();
    }

    private PostEngagement.Tag defaultTagFor(UUID postId) {
        PostEngagement.Tag[] tags = PostEngagement.Tag.values();
        if (postId == null) return PostEngagement.Tag.ACADEMIC;
        return tags[Math.floorMod(postId.hashCode(), tags.length)];
    }
}
