package com.example.moderationapp.data.persistence.serialization;

import com.example.moderationapp.data.model.Report;
import java.util.UUID;

public class ReportSerializer implements Serializer<Report, String[]> {
    @Override
    public String[] serialize(Report object) {
        return new String[]{
                object.message().toString(),
                object.user().toString(),
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
