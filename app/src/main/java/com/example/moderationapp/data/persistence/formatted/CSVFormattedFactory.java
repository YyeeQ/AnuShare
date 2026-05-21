package com.example.moderationapp.data.persistence.formatted;

import java.io.Reader;
import java.io.Writer;

public class CSVFormattedFactory implements FormattedFactory<String[]> {
    private final CSVFormat format;

    public CSVFormattedFactory(CSVFormat format) {
        this.format = format;
    }

    @Override
    public FormattedWriter<String[]> writer(Writer documentWriter) {
        return new CSVWriter(format, documentWriter);
    }

    @Override
    public FormattedReader<String[]> reader(Reader documentReader) {
        return new CSVReader(format, documentReader);
    }
}
