package moderation;

import dao.PostDAO;
import dao.ReportDAO;
import dao.UserDAO;
import dao.model.Message;
import dao.model.Post;

import java.util.Iterator;
import java.util.UUID;

/**
 * Static facade exposing the moderation-tools API to the rest of the application.
 * <p>
 * This class holds no state itself; all data lives in the relevant DAOs
 * ({@link UserDAO}, {@link PostDAO}, {@link ReportDAO}). Validating that the
 * referenced message and user exist is done here — we ask the DAOs rather than
 * tracking it ourselves.
 */
public class ModerationTools {

	// --------------------------- Task 1 ---------------------------

	/**
	 * Records a user's report on a message.
	 * @param message   the UUID of the reported message
	 * @param user      the UUID of the reporting user
	 * @param timestamp the time of the report, in UNIX ms
	 * @return true if the report was newly stored; false if either UUID does
	 *         not refer to an existing entity, or if the user has already
	 *         reported this message
	 */
	public static boolean addReport(UUID message, UUID user, long timestamp) {
		if (message == null || user == null) return false;
		if (!messageExists(message)) return false;
		if (UserDAO.getInstance().getByUUID(user) == null) return false;

		return ReportDAO.getInstance().addReport(message, user, timestamp);
	}

	/**
	 * Retracts a user's existing report on a message.
	 * <p>
	 * Note: the {@code timestamp} parameter is accepted for API symmetry with
	 * {@link #addReport} but is not used — a user has at most one active report
	 * on any given message, so the (message, user) pair uniquely identifies it.
	 *
	 * @param message   the UUID of the message
	 * @param user      the UUID of the user retracting their report
	 * @param timestamp unused; accepted for API symmetry
	 * @return true if a matching report was found and removed; false if either
	 *         UUID does not exist or the user had no active report on this message
	 */
	public static boolean removeReport(UUID message, UUID user, long timestamp) {
		if (message == null || user == null) return false;
		if (!messageExists(message)) return false;
		if (UserDAO.getInstance().getByUUID(user) == null) return false;

		return ReportDAO.getInstance().removeReport(message, user);
	}

	/**
	 * Checks whether a particular user has an active report on a particular message.
	 * @param message the UUID of the message
	 * @param user    the UUID of the user
	 * @return true if such a report currently exists, false otherwise (including
	 *         when either UUID does not exist)
	 */
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
	 * Linear scan over every Message stored in every Post to confirm a UUID
	 * refers to a real Message. The project's data model has no global
	 * Message-by-UUID index, so this is the cleanest available check.
	 * <p>
	 * If profiling later shows this is a hotspot, the natural fix is to add
	 * a {@code MessageDAO} (or a UUID→Message index in {@link PostDAO}) and
	 * delegate this lookup to it. That refactor is out of scope for Task 1.
	 *
	 * @param messageId the UUID to look up
	 * @return true if some Post contains a Message with this id, false otherwise
	 */
	private static boolean messageExists(UUID messageId) {
		Iterator<Message> it = PostDAO.getInstance().getAllMessages();
		while (it.hasNext()) {
			if (it.next().id().equals(messageId)) return true;
		}
		return false;
	}
}