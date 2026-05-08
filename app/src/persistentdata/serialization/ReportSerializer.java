package persistentdata.serialization;

import dao.model.Report;

import java.util.UUID;

/**
 * Converts between Reports and String[] using schema: messageId, userId, timestamp
 */
public class ReportSerializer implements Serializer<Report, String[]> {

    @Override
    public String[] serialize(Report object) {
        return new String[]{
                object.messageId().toString(),
                object.userId().toString(),
                String.valueOf(object.timestamp())
        };
    }

    @Override
    public Report deserialize(String[] data) {
        return new Report(
                UUID.fromString(data[0]),
                UUID.fromString(data[1]),
                Long.parseLong(data[2])
        );
    }
}
