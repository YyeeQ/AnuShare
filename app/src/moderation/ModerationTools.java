package moderation;

import dao.PostDAO;
import dao.ReportDAO;
import dao.UserDAO;
import dao.model.Message;
import dao.model.Post;
import dao.model.User;

import java.util.Iterator;
import java.util.UUID;

/**
 * Static facade exposing the moderation-tools API to the rest of the application.
 * <p>
 * This class holds no state itself; all data lives in the relevant DAOs
 * ({@link UserDAO}, {@link PostDAO}, {@link ReportDAO}) or on the {@link Post}
 * objects themselves (for hidden-message state). Existence checks for users
 * and messages are performed here before delegating.
 */
public class ModerationTools {

	// --------------------------- Task 1 ---------------------------

	/**
	 * Records a user's report on a message.
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
	 * The {@code timestamp} parameter is accepted for API symmetry with
	 * {@link #addReport} but is not used: a user has at most one active report
	 * on any given message, so the (message, user) pair uniquely identifies it.
	 *
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
	 * @return true if the given user has an active report on the given message
	 */
	public static boolean hasReported(UUID message, UUID user) {
		if (message == null || user == null) return false;
		if (!messageExists(message)) return false;
		if (UserDAO.getInstance().getByUUID(user) == null) return false;

		return ReportDAO.getInstance().hasReported(message, user);
	}

	// --------------------------- Task 2 ---------------------------

	/**
	 * Sets the hidden state of a message. Only Admin users may invoke this.
	 * <p>
	 * Both UUIDs must refer to existing entities, and {@code user} must be an
	 * {@link User.Role#Admin}; otherwise this method returns false without
	 * making any change. When the checks pass, the message's hidden flag on
	 * its containing Post is updated to {@code hidden}.
	 *
	 * @param message the UUID of the message to hide or un-hide
	 * @param user    the UUID of the user requesting the change
	 * @param hidden  the desired hidden state (true = hidden, false = visible)
	 * @return true if the operation was performed, false otherwise
	 */
	public static boolean setHidden(UUID message, UUID user, boolean hidden) {
		if (message == null || user == null) return false;

		User actor = UserDAO.getInstance().getByUUID(user);
		if (actor == null || actor.role() != User.Role.Admin) return false;

		Post containingPost = findPostContaining(message);
		if (containingPost == null) return false;

		// Idempotent semantics: setting hidden=true on an already-hidden message
		// (or hidden=false on an already-visible message) succeeds and reports true,
		// because the post-condition the caller asked for is satisfied.
		if (hidden) {
			containingPost.hide(message);
		} else {
			containingPost.unhide(message);
		}
		return true;
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
	 *
	 * @param messageId the UUID to look up
	 * @return true if some Post contains a Message with this id, false otherwise
	 */
	private static boolean messageExists(UUID messageId) {
		return findPostContaining(messageId) != null;
	}

	/**
	 * Finds the Post that contains the message with the given UUID, if any.
	 * @param messageId the UUID of the message to locate
	 * @return the containing Post, or null if no Post contains this message
	 */
	private static Post findPostContaining(UUID messageId) {
		for (Iterator<Post> postIt = PostDAO.getInstance().getAll(); postIt.hasNext(); ) {
			Post post = postIt.next();
			for (Iterator<Message> msgIt = post.messages.getAll(); msgIt.hasNext(); ) {
				if (msgIt.next().id().equals(messageId)) return post;

			}
		}
		return null;
	}
}