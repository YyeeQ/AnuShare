package dao.model;
import dao.MessageComparator;
import moderation.ModerationTools;
import sorteddata.*;
import java.util.*;

public class Post implements HasUUID {
	public final UUID id;
	public final UUID poster;
	public final String topic;
    public final SortedData<Message> messages;

	public Post(UUID id, UUID poster, String topic) {
		this.id = id;
		this.poster = poster;
		this.topic = topic;
		this.messages = SortedDataFactory.makeSortedData(MessageComparator.getInstance());
	}

	public Post(UUID id) {
		this(id, null, null);
	}

	public SortedData<Message> getVisibleMessages(boolean isAdmin) {
		if (isAdmin) {
			return messages;
		}
		SortedData<Message> visibleMessages =
				SortedDataFactory.makeSortedData(MessageComparator.getInstance());
		Iterator<Message> iterator = messages.getAll();
		while (iterator.hasNext()) {
			Message message = iterator.next();
			if (!ModerationTools.isHidden(message.id())) {
				visibleMessages.insert(message);
			}
		}
		return visibleMessages;
	}

	public UUID getUUID() { return id; }
}
