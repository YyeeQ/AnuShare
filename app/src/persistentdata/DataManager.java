package persistentdata;

import dao.PostDAO;
import dao.UserDAO;
import dao.model.Message;
import dao.model.Post;
import dao.model.User;
import persistentdata.formatted.CSVFormat;
import persistentdata.formatted.CSVFormattedFactory;
import persistentdata.io.ComputerIOFactory;
import persistentdata.io.IOFactory;
import persistentdata.serialization.MessageSerializer;
import persistentdata.serialization.PostSerializer;
import persistentdata.serialization.UserSerializer;

public class DataManager {
	private static DataManager instance;
	public static DataManager getInstance() {
		if (instance == null)
			instance = new DataManager();
		return instance;
	}

	private final IOFactory IO = new ComputerIOFactory();

	private final DataPipeline<User, String[]> userPipeline = new DataPipeline<>(
			IO, new CSVFormattedFactory(new CSVFormat(4)), new UserSerializer(), "users");

	private final DataPipeline<Post, String[]> postPipeline = new DataPipeline<>(
			IO, new CSVFormattedFactory(new CSVFormat(3)), new PostSerializer(), "posts");

	// 6 columns now: id, poster, thread, timestamp, message text, hidden flag
	private final DataPipeline<Message, String[]> messagePipeline = new DataPipeline<>(
			IO, new CSVFormattedFactory(new CSVFormat(6)), new MessageSerializer(), "messages");

	private final DataPipeline<Report, String[]> reportPipeline = new DataPipeline<>(
			IO, new CSVFormattedFactory(new CSVFormat(3)), new ReportSerializer(), "reports");

	private final UserDAO users = UserDAO.getInstance();
	private final PostDAO posts = PostDAO.getInstance();
	private final ReportDAO reports = ReportDAO.getInstance();

	public void readAll() {
		users.clear();
		posts.clear();
		reports.clear();

		userPipeline.readTo(users::add);
		postPipeline.readTo(posts::add);
		messagePipeline.readTo((message) ->
				posts.get(new Post(message.thread())).messages.insert(message));
		reportPipeline.readTo(reports::addExisting);
	}

	public void writeAll() {
		userPipeline.writeFrom(users.getAll());
		postPipeline.writeFrom(posts.getAll());
		messagePipeline.writeFrom(posts.getAllMessages());
		reportPipeline.writeFrom(reports.allReports());
	}
}
