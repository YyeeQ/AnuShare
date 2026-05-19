package com.example.moderationapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.moderationapp.data.dao.MessageComparator;
import com.example.moderationapp.data.dao.PostDAO;
import com.example.moderationapp.data.dao.ReportDAO;
import com.example.moderationapp.data.dao.UserDAO;
import com.example.moderationapp.data.model.Message;
import com.example.moderationapp.data.model.Post;
import com.example.moderationapp.data.model.Report;
import com.example.moderationapp.data.model.User;
import com.example.moderationapp.data.persistence.DataManager;
import com.example.moderationapp.data.persistence.DataPipeline;
import com.example.moderationapp.data.persistence.serialization.MessageSerializer;
import com.example.moderationapp.data.persistence.serialization.ReportSerializer;
import com.example.moderationapp.logic.moderation.ModerationTools;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.UUID;

public class ModerationToolsAddReportTests {
    private User reporter;
    private Post post;
    private Message message;

    @Before
    public void setUp() {
        UserDAO.getInstance().clear();
        PostDAO.getInstance().clear();
        ReportDAO.getInstance().clear();

        reporter = new User(UUID.randomUUID(), User.Role.Member, "reporter", "password");
        User otherReporter = new User(UUID.randomUUID(), User.Role.Member, "otherReporter", "password");
        post = new Post(UUID.randomUUID(), reporter.id(), "Task 5 test post");
        message = new Message(UUID.randomUUID(), reporter.id(), post.id, 100L, "message under test");

        assertTrue(UserDAO.getInstance().add(reporter));
        assertTrue(UserDAO.getInstance().add(otherReporter));
        assertTrue(PostDAO.getInstance().add(post));
        assertTrue(post.messages.insert(message));
    }

    @After
    public void tearDown() {
        UserDAO.getInstance().clear();
        PostDAO.getInstance().clear();
        ReportDAO.getInstance().clear();
    }

    @Test(timeout = 1000)
    public void addReportSucceedsForExistingMessageAndUser() {
        assertTrue(ModerationTools.addReport(message.id(), reporter.id(), 10L));
        assertTrue(ModerationTools.hasReported(message.id(), reporter.id()));
        assertEquals(1, activeReportCount());
    }

    @Test(timeout = 1000)
    public void addReportRejectsMissingMessage() {
        assertFalse(ModerationTools.addReport(UUID.randomUUID(), reporter.id(), 10L));
        assertFalse(ModerationTools.hasReported(message.id(), reporter.id()));
        assertEquals(0, activeReportCount());
    }

    private static int activeReportCount() {
        int count = 0;
        Iterator<Report> reports = ReportDAO.getInstance().allReports();
        while (reports.hasNext()) {
            reports.next();
            count++;
        }
        return count;
    }
}
