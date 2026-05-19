package com.example.moderationapp.data.persistence.formatted;

import com.example.moderationapp.data.persistence.PersistentDataException;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;

public class CSVReader implements FormattedReader<String[]> {
    private final CSVFormat format;
    private final PushbackReader reader;
    private boolean eof = false;

    public CSVReader(CSVFormat format, Reader reader) {
        this.format = format;
        this.reader = new PushbackReader(reader);
    }

    @Override
    public boolean hasNext() {
        if (eof) return false;
        try {
            int next = reader.read();
            if (next == -1) {
                eof = true;
                reader.close();
                return false;
            }
            reader.unread(next);
            return true;
        } catch (IOException e) {
            throw new CSVIOException(e.getMessage());
        }
    }

    @Override
    public String[] getNext() {
        if (format.COLUMN_COUNT == 0) {
            throw new CSVIOException("Line was too long: expected 0 fields");
        }

        if (eof) {
            throw new CSVIOException("Already reached end of file while reading");
        }

        StringBuilder result = new StringBuilder();
        boolean inSpecialField = false;
        boolean lastWasEscape = false;
        String[] fields = new String[format.COLUMN_COUNT];
        int fieldIndex = 0;
        boolean ignoredFirstQuote = false;
        
        while (true) {
            char c;
            try {
                int i = reader.read();
                if (i == -1) {
                    if (inSpecialField) {
                        throw new CSVIOException("EOF reached unexpectedly while escaped");
                    }
                    eof = true;
                    reader.close();
                    if (fieldIndex < format.COLUMN_COUNT - 1) {
                         // Fall through to handle short line
                    } else if (fieldIndex == format.COLUMN_COUNT - 1) {
                        fields[fieldIndex] = result.toString();
                        return fields;
                    }
                    break;
                } else {
                    c = (char) i;
                }
            } catch (IOException e) {
                throw new CSVIOException(e.getMessage());
            }

            if (c == format.ESCAPE_MARKER) {
                inSpecialField = !inSpecialField;
                if (result.length() == 0 && !ignoredFirstQuote) {
                    ignoredFirstQuote = true;
                } else if (lastWasEscape) {
                    result.append(c);
                    lastWasEscape = false;
                } else {
                    lastWasEscape = true;
                }
            } else {
                lastWasEscape = false;
                if (c == format.FIELD_SEPARATOR && !inSpecialField) {
                    fields[fieldIndex] = result.toString();
                    fieldIndex++;
                    if (fieldIndex >= format.COLUMN_COUNT) {
                        throw new CSVIOException("Line was too long: expected " + format.COLUMN_COUNT + " fields");
                    }
                    ignoredFirstQuote = false;
                    result = new StringBuilder();
                } else if (c == format.LINE_SEPARATOR && !inSpecialField) {
                    fields[fieldIndex] = result.toString();
                    fieldIndex++;
                    if (fieldIndex < format.COLUMN_COUNT) {
                        throw new CSVIOException("Line was too short: expected " + format.COLUMN_COUNT + " fields but found " + fieldIndex);
                    }
                    break;
                } else {
                    result.append(c);
                }
            }
        }
        return fields;
    }

    public static class CSVIOException extends PersistentDataException {
        public CSVIOException(String message) {
            super(message);
        }
    }
}
