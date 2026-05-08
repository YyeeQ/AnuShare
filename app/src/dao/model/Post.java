package dao.model;

import dao.MessageComparator;
import sorteddata.SortedData;
import sorteddata.SortedDataFactory;

import java.util.Comparator;
import java.util.Iterator;
import java.util.UUID;

/**
 * Represents a single post (a top-level thread) and the messages replying to it.
 * <p>
 * In addition to its visible message list, a Post tracks which of those messages
 * have been hidden by a moderator. Hidden messages remain in {@link #messages} so
 * that admins can still see them; the hidden-set is used to filter them out for
 * non-admin viewers in {@link #getVisibleMessages(boolean)}.
 */
public class Post implements HasUUID {
	public final UUID id;
	public final UUID poster;
	public final String topic;
	public final SortedData<Message> messages;

	/**
	 * UUIDs of messages on this post that are currently hidden from non-admin users.
	 * <p>
	 * Stored as a SortedData of UUIDs so that membership checks are O(log n),
	 * matching the project's pattern of using SortedData wherever a Set/Map would
	 * normally be used.
	 * <p>
	 * Non-final because {@link #unhide(UUID)} rebuilds it without the target entry
	 * since SortedData has no remove operation.
	 */
	private SortedData<UUID> hiddenMessages;

	private static final Comparator<UUID> UUID_COMPARATOR = new Comparator<UUID>() {
		@Override
		public int compare(UUID a, UUID b) {
			return a.compareTo(b);
		}
	};

	public Post(UUID id, UUID poster, String topic) {
		this.id = id;
		this.poster = poster;
		this.topic = topic;
		this.messages = SortedDataFactory.makeSortedData(MessageComparator.getInstance());
		this.hiddenMessages = SortedDataFactory.makeSortedData(UUID_COMPARATOR);
	}

	public Post(UUID id) {
		this(id, null, null);
	}

	@Override
	public UUID getUUID() {
		return id;
	}

	// --------------------------- hidden-message API ---------------------------

	/**
	 * Marks a message on this post as hidden.
	 *
	 * @param messageId the UUID of the message to hide
	 * @return true if the message was newly hidden, false if it was already hidden
	 */
	public boolean hide(UUID messageId) {
		return hiddenMessages.insert(messageId);
	}

	/**
	 * Removes the hidden flag from a message on this post.
	 *
	 * @param messageId the UUID of the message to un-hide
	 * @return true if the message was previously hidden and is now un-hidden,
	 *         false if it was not hidden in the first place
	 */
	public boolean unhide(UUID messageId) {
		if (hiddenMessages.get(messageId) == null) {
			return false;
		}

		SortedData<UUID> rebuilt = SortedDataFactory.makeSortedData(UUID_COMPARATOR);

		for (Iterator<UUID> it = hiddenMessages.getAll(); it.hasNext(); ) {
			UUID currentId = it.next();

			if (!currentId.equals(messageId)) {
				rebuilt.insert(currentId);
			}
		}

		hiddenMessages = rebuilt;
		return true;
	}

	/**
	 * @param messageId the UUID to check
	 * @return true if this message is currently flagged hidden, false otherwise
	 */
	public boolean isHidden(UUID messageId) {
		return hiddenMessages.get(messageId) != null;
	}

	/**
	 * @return an iterator over every message currently hidden on this post.
	 *         Used by the persistence layer to serialise hidden state.
	 */
	public Iterator<UUID> getHiddenMessageIds() {

		return hiddenMessages.getAll();
	}

	// --------------------------- Task 2: visibility filter ---------------------------

	/**
	 * Returns the messages on this post that should be visible to a viewer.
	 * <p>
	 * Admins see every message regardless of its hidden status; non-admins
	 * see only messages whose UUID is not currently flagged hidden.
	 *
	 * @param isAdmin whether the viewer has admin privileges
	 * @return a SortedData containing the messages this viewer is allowed to see
	 */
	public SortedData<Message> getVisibleMessages(boolean isAdmin) {
		if (isAdmin) {
			return messages;
		}

		SortedData<Message> visible =
				SortedDataFactory.makeSortedData(MessageComparator.getInstance());

		for (Iterator<Message> it = messages.getAll(); it.hasNext(); ) {
			Message message = it.next();

			if (!isHidden(message.id())) {
				visible.insert(message);
			}
		}

		return visible;
	}
}