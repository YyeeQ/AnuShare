package com.example.moderationapp.logic.moderation;

import com.example.moderationapp.data.dao.PostDAO;
import com.example.moderationapp.data.dao.ReportDAO;
import com.example.moderationapp.data.dao.UserDAO;
import com.example.moderationapp.data.model.Message;
import com.example.moderationapp.data.model.MessageReports;
import com.example.moderationapp.data.model.User;
import com.example.moderationapp.logic.moderation.strategy.ReportStrategy;
import com.example.moderationapp.logic.moderation.strategy.ReportStrategyFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;

public class ModerationTools {

    public static boolean addReport(UUID message, UUID user, long timestamp) {
        if (message == null || user == null) return false;
        if (!messageExists(message)) return false;
        if (UserDAO.getInstance().getByUUID(user) == null) return false;
        return ReportDAO.getInstance().addReport(message, user, timestamp);
    }

    public static boolean removeReport(UUID message, UUID user, long timestamp) {
        if (message == null || user == null) return false;
        if (!messageExists(message)) return false;
        if (UserDAO.getInstance().getByUUID(user) == null) return false;
        return ReportDAO.getInstance().removeReport(message, user);
    }

    public static boolean hasReported(UUID message, UUID user) {
        if (message == null || user == null) return false;
        if (!messageExists(message)) return false;
        if (UserDAO.getInstance().getByUUID(user) == null) return false;
        return ReportDAO.getInstance().hasReported(message, user);
    }

    public static boolean setHidden(UUID message, UUID user, boolean hidden) {
        if (message == null || user == null) return false;
        User actor = UserDAO.getInstance().getByUUID(user);
        if (actor == null || actor.role() != User.Role.Admin) return false;
        Message targetMessage = getMessageByUUID(message);
        if (targetMessage == null) return false;
        targetMessage.setHidden(hidden);
        return true;
    }

    public static Iterator<Message> getReportedMessages(String strategy, int amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");

        ReportStrategy chosen = ReportStrategyFactory.create(strategy);

        ArrayList<MessageReports> ranked = new ArrayList<>();
        for (Iterator<MessageReports> it = ReportDAO.getInstance().allBuckets(); it.hasNext(); ) {
            MessageReports bucket = it.next();
            if (!bucket.isEmpty()) ranked.add(bucket);
        }
        ranked.sort(chosen.comparator());

        int limit = Math.min(amount, ranked.size());
        ArrayList<Message> result = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            Message m = getMessageByUUID(ranked.get(i).getUUID());
            if (m != null) result.add(m);
        }
        return result.iterator();
    }

    private static boolean messageExists(UUID messageId) {
        return getMessageByUUID(messageId) != null;
    }

    private static Message getMessageByUUID(UUID messageId) {
        if (messageId == null) return null;
        Iterator<Message> it = PostDAO.getInstance().getAllMessages();
        while (it.hasNext()) {
            Message message = it.next();
            if (message.id().equals(messageId)) return message;
        }
        return null;
    }
}
