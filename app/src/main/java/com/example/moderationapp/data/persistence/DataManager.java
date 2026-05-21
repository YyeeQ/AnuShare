package com.example.moderationapp.data.persistence;

import com.example.moderationapp.data.dao.PostDAO;
import com.example.moderationapp.data.dao.PostEngagementDAO;
import com.example.moderationapp.data.dao.ReportDAO;
import com.example.moderationapp.data.dao.UserDAO;
import com.example.moderationapp.data.model.Message;
import com.example.moderationapp.data.model.Post;
import com.example.moderationapp.data.model.PostEngagement;
import com.example.moderationapp.data.model.Report;
import com.example.moderationapp.data.model.User;
import com.example.moderationapp.data.persistence.formatted.CSVFormat;
import com.example.moderationapp.data.persistence.formatted.CSVFormattedFactory;
import com.example.moderationapp.data.persistence.io.ComputerIOFactory;
import com.example.moderationapp.data.persistence.io.IOFactory;
import com.example.moderationapp.data.persistence.serialization.MessageSerializer;
import com.example.moderationapp.data.persistence.serialization.LegacyPostEngagementSerializer;
import com.example.moderationapp.data.persistence.serialization.PostEngagementSerializer;
import com.example.moderationapp.data.persistence.serialization.PostSerializer;
import com.example.moderationapp.data.persistence.serialization.ReportDetailSerializer;
import com.example.moderationapp.data.persistence.serialization.ReportSerializer;
import com.example.moderationapp.data.persistence.serialization.UserSerializer;

public class DataManager {
    private static DataManager instance;

    public static DataManager getInstance() {
        if (instance == null)
            instance = new DataManager();
        return instance;
    }

    private IOFactory IO = new ComputerIOFactory();

    private DataPipeline<User, String[]> userPipeline;
    private DataPipeline<Post, String[]> postPipeline;
    private DataPipeline<Message, String[]> messagePipeline;
    private DataPipeline<PostEngagement, String[]> legacyPostEngagementPipeline;
    private DataPipeline<PostEngagement, String[]> postEngagementPipeline;
    private DataPipeline<Report, String[]> legacyReportPipeline;
    private DataPipeline<Report, String[]> reportPipeline;

    private final UserDAO users = UserDAO.getInstance();
    private final PostDAO posts = PostDAO.getInstance();
    private final PostEngagementDAO postEngagements = PostEngagementDAO.getInstance();
    private final ReportDAO reports = ReportDAO.getInstance();

    private DataManager() {
        rebuildPipelines();
    }

    public void configureIO(IOFactory ioFactory) {
        if (ioFactory == null) return;
        this.IO = ioFactory;
        rebuildPipelines();
    }

    private void rebuildPipelines() {
        userPipeline = new DataPipeline<>(
                IO, new CSVFormattedFactory(new CSVFormat(4)), new UserSerializer(), "users");

        postPipeline = new DataPipeline<>(
                IO, new CSVFormattedFactory(new CSVFormat(3)), new PostSerializer(), "posts");

        messagePipeline = new DataPipeline<>(
                IO, new CSVFormattedFactory(new CSVFormat(6)), new MessageSerializer(), "messages");

        legacyPostEngagementPipeline = new DataPipeline<>(
                IO, new CSVFormattedFactory(new CSVFormat(5)), new LegacyPostEngagementSerializer(), "post_engagements");

        postEngagementPipeline = new DataPipeline<>(
                IO, new CSVFormattedFactory(new CSVFormat(7)), new PostEngagementSerializer(), "post_engagement_details");

        legacyReportPipeline = new DataPipeline<>(
                IO, new CSVFormattedFactory(new CSVFormat(3)), new ReportSerializer(), "reports");

        reportPipeline = new DataPipeline<>(
                IO, new CSVFormattedFactory(new CSVFormat(5)), new ReportDetailSerializer(), "report_details");
    }

    public void readAll() {
        users.clear();
        posts.clear();
        postEngagements.clear();
        reports.clear();

        userPipeline.readTo(users::add);
        postPipeline.readTo(posts::add);
        messagePipeline.readTo((message) -> {
            Post post = posts.get(new Post(message.thread()));
            if (post != null) post.messages.insert(message);
        });
        boolean[] readDetailedEngagements = new boolean[] {false};
        postEngagementPipeline.readTo(engagement -> {
            readDetailedEngagements[0] = true;
            postEngagements.add(engagement);
        });
        if (!readDetailedEngagements[0]) {
            legacyPostEngagementPipeline.readTo(postEngagements::add);
        }
        boolean[] readDetailedReports = new boolean[] {false};
        reportPipeline.readTo(report -> {
            readDetailedReports[0] = true;
            reports.addExisting(report);
        });
        if (!readDetailedReports[0]) {
            legacyReportPipeline.readTo(reports::addExisting);
        }
    }

    public void writeAll() {
        userPipeline.writeFrom(users.getAll());
        postPipeline.writeFrom(posts.getAll());
        messagePipeline.writeFrom(posts.getAllMessages());
        postEngagementPipeline.writeFrom(postEngagements.getAll());
        reportPipeline.writeFrom(reports.allReports());
    }
}
