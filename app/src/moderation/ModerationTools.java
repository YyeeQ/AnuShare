package moderation;

import dao.PostDAO;
import dao.ReportDAO;
import dao.UserDAO;
import dao.model.Message;
import dao.model.User;

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

	// --------------------------- Task 2 ---------------------------

	public static boolean setHidden(UUID message, UUID user, boolean hidden) {
		if (message == null || user == null) return false;
		User actor = UserDAO.getInstance().getByUUID(user);
		if (actor == null || actor.role() != User.Role.Admin) return false;
		Message targetMessage = getMessageByUUID(message);
		if (targetMessage == null) return false;
		targetMessage.setHidden(hidden);
		return true;
	}

	// --------------------------- Task 4 (placeholder) ---------------------------

	public static Iterator<Message> getReportedMessages(String strategy, int amount) {
		// TODO: task 4
		return null;
	}

	// --------------------------- helpers ---------------------------

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