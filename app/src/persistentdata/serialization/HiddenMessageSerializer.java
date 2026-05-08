package persistentdata.serialization;

import java.util.UUID;

/**
 * Converts between hidden message UUIDs and String[] using schema: messageId
 */
public class HiddenMessageSerializer implements Serializer<UUID, String[]> {

    @Override
    public String[] serialize(UUID object) {
        return new String[]{object.toString()};
    }

    @Override
    public UUID deserialize(String[] data) {
        return UUID.fromString(data[0]);
    }
}
