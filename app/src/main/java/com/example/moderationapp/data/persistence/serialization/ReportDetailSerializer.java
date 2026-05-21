package com.example.moderationapp.data.persistence.serialization;

import com.example.moderationapp.data.model.Report;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ReportDetailSerializer implements Serializer<Report, String[]> {
    @Override
    public String[] serialize(Report object) {
        return new String[]{
                object.message().toString(),
                object.user().toString(),
                String.valueOf(object.timestamp()),
                serializeTypes(object.types()),
                object.reason()
        };
    }

    @Override
    public Report deserialize(String[] data) {
        return new Report(
                UUID.fromString(data[0]),
                UUID.fromString(data[1]),
                Long.parseLong(data[2]),
                deserializeTypes(data[3]),
                data[4]
        );
    }

    private String serializeTypes(List<Report.Type> types) {
        return types.stream()
                .map(Report.Type::name)
                .collect(Collectors.joining("|"));
    }

    private List<Report.Type> deserializeTypes(String value) {
        List<Report.Type> types = new ArrayList<>();
        if (value != null && !value.isEmpty()) {
            String[] stored = value.split("\\|");
            for (String item : stored) {
                if (!item.isEmpty()) types.add(Report.Type.fromStorage(item));
            }
        }
        if (types.isEmpty()) types.add(Report.Type.OTHER);
        return types;
    }
}
