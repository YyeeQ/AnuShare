package com.example.moderationapp.data.persistence.formatted;

public final class CSVFormat {
    public final char FIELD_SEPARATOR;
    public final char LINE_SEPARATOR;
    public final char ESCAPE_MARKER;
    public final int COLUMN_COUNT;

    public CSVFormat(char fieldSeparator, char lineSeparator, char escapeMarker, int columnCount) {
        if (fieldSeparator == lineSeparator || fieldSeparator == escapeMarker || lineSeparator == escapeMarker)
            throw new RuntimeException("The three special delimiters in CSVFormat must be different");
        this.FIELD_SEPARATOR = fieldSeparator;
        this.LINE_SEPARATOR = lineSeparator;
        this.ESCAPE_MARKER = escapeMarker;
        this.COLUMN_COUNT = columnCount;
    }

    public CSVFormat(int columnCount) {
        this(',', '\n', '"', columnCount);
    }
}
