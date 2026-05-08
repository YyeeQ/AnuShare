package moderation;

import dao.PostDAO;
import dao.ReportDAO;
import dao.UserDAO;
import dao.model.Message;
import dao.model.Post;

import java.util.Iterator;
import java.util.UUID;

/**
 * Static facade for the moderation API. Holds no state itself — all data
 * lives in the relevant DAOs or on the entities themselves.
 */
public class ModerationTools {

	// --------------------------- Task 1 ---------------------------

	public static boolean addReport(UUID message, UUID user, long timestamp) {
		if (message == null || user == null) return false;
		if (!messageExists(message)) return false;
		if (UserDAO.getInstance().getByUUID(user) == null) return false;

		return ReportDAO.getInstance().addReport(message, user, timestamp);
	}

	/**
	 * The {@code timestamp} parameter is unused: the (message, user) pair
	 * uniquely identifies a report. Kept for API symmetry with addReport.
	 */
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

	// --------------------------- Task 2 (placeholder) ---------------------------

	public static boolean setHidden(UUID message, UUID user, boolean hidden) {
		// TODO: task 2
		return false;
	}

	// --------------------------- Task 4 (placeholder) ---------------------------

	public static Iterator<Message> getReportedMessages(String strategy, int amount) {
		// TODO: task 4
		return null;
	}

	// --------------------------- helpers ---------------------------

	/**
	 * No global Message-by-UUID index exists, so we scan. This is the cleanest
	 * available check for a UUID's existence; if it ever shows up in profiling,
	 * the fix is a MessageDAO.
	 */
	private static boolean messageExists(UUID messageId) {
		Iterator<Message> it = PostDAO.getInstance().getAllMessages();
		while (it.hasNext()) {
			if (it.next().id().equals(messageId)) return true;
		}
		return false;
	}
}