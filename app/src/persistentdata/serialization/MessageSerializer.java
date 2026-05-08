package persistentdata.serialization;

import dao.model.Message;


import java.util.UUID;

/**
 * 6-column schema: id, poster, thread, timestamp, message text, hidden flag.
 * The hidden flag is "1" or "0" — a portable cross-language boolean encoding.
 */
public class MessageSerializer implements Serializer<Message, String[]> {

	@Override
	public String[] serialize(Message object) {
		return new String[] {
				object.id().toString(),
				object.poster().toString(),
				object.thread().toString(),
				String.valueOf(object.timestamp()),
				object.message(),
				object.isHidden() ? "1" : "0"
		};
	}

	@Override
	public Message deserialize(String[] data) {
		return new Message(
				UUID.fromString(data[0]),
				UUID.fromString(data[1]),
				UUID.fromString(data[2]),
				Long.parseLong(data[3]),
				data[4],
				"1".equals(data[5])
		);
	}
}