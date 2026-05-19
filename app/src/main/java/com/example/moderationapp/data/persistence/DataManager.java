package com.example.moderationapp.data.persistence;

import com.example.moderationapp.data.dao.PostDAO;
import com.example.moderationapp.data.dao.ReportDAO;
import com.example.moderationapp.data.dao.UserDAO;
import com.example.moderationapp.data.model.Message;
import com.example.moderationapp.data.model.Post;
import com.example.moderationapp.data.model.Report;
import com.example.moderationapp.data.model.User;
import com.example.moderationapp.data.persistence.formatted.CSVFormat;
import com.example.moderationapp.data.persistence.formatted.CSVFormattedFactory;
import com.example.moderationapp.data.persistence.io.ComputerIOFactory;
import com.example.moderationapp.data.persistence.io.IOFactory;
import com.example.moderationapp.data.persistence.serialization.MessageSerializer;
import com.example.moderationapp.data.persistence.serialization.PostSerializer;
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
    private DataPipeline<Report, String[]> reportPipeline;

    private final UserDAO users = UserDAO.getInstance();
    private final PostDAO posts = PostDAO.getInstance();
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

        reportPipeline = new DataPipeline<>(
                IO, new CSVFormattedFactory(new CSVFormat(3)), new ReportSerializer(), "reports");
    }

    public void readAll() {
        users.clear();
        posts.clear();
        reports.clear();

        userPipeline.readTo(users::add);
        postPipeline.readTo(posts::add);
        messagePipeline.readTo((message) -> {
            Post post = posts.get(new Post(message.thread()));
            if (post != null) post.messages.insert(message);
        });
        reportPipeline.readTo(reports::addExisting);
    }

    public void writeAll() {
        userPipeline.writeFrom(users.getAll());
        postPipeline.writeFrom(posts.getAll());
        messagePipeline.writeFrom(posts.getAllMessages());
        reportPipeline.writeFrom(reports.allReports());
    }
}
