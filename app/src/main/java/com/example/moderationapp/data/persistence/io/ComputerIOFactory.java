package com.example.moderationapp.data.persistence.io;

import java.io.*;

public class ComputerIOFactory implements IOFactory {
    private static final String FULL_FILENAME_TEMPLATE = "saved/%s.txt";

    private String parseFullFilename(String file) {
        return String.format(FULL_FILENAME_TEMPLATE, file);
    }

    @Override
    public Writer writer(String filename) {
        try {
            File file = new File(parseFullFilename(filename));
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            return new FileWriter(file);
        } catch (IOException ignored) {
            return null;
        }
    }

    @Override
    public Reader reader(String filename) {
        try {
            return new FileReader(parseFullFilename(filename));
        } catch (IOException ignored) {
            return null;
        }
    }
}
