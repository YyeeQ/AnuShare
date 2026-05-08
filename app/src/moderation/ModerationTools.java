package moderation;

import dao.*;

import dao.model.*;

import java.util.*;

public class ModerationTools {
	private static final Set<UUID> hiddenMessages = new HashSet<>();
	public static boolean addReport(UUID message, UUID user, long timestamp) {
		// TODO: task 1
		return false;
	}
	
	public static boolean removeReport(UUID message, UUID user, long timestamp) {
		// TODO: task 1
		return false;
	}
	
	public static boolean hasReported(UUID message, UUID user) {
		// TODO: task 1
		return false;
	}
	


	public static boolean setHidden(UUID message, UUID user, boolean hidden) {
		// TODO: task 2
		Message foundMessage = getMessageByUUID(message);
		if (foundMessage == null) {
			return false;
		}

		User foundUser = UserDAO.getInstance().getByUUID(user);
		if (foundUser == null) {
			return false;
		}

		if (foundUser.role() != User.Role.Admin) {
			return false;
		}

		if (hidden) {
			hiddenMessages.add(message);
		} else {
			hiddenMessages.remove(message);
		}
		return true;
	}

	public static boolean isHidden(UUID message) {
		return hiddenMessages.contains(message);
	}

	private static Message getMessageByUUID(UUID messageId) {
		Iterator<Message> messages = PostDAO.getInstance().getAllMessages();
		while (messages.hasNext()) {
			Message message = messages.next();
			if (message.id().equals(messageId)) {
				return message;
			}
		}
		return null;
	}
	
	public static Iterator<Message> getReportedMessages(String strategy, int amount) {
		// TODO: task 4
		return null;
	}
}
